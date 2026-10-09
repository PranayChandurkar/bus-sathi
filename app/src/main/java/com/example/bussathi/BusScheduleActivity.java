package com.example.bussathi;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BusScheduleActivity extends AppCompatActivity {

    private static final String TAG = "BusScheduleActivity";

    private RadioGroup rgSchedulePeriod;
    private RadioButton rbMorning, rbEvening;
    private RadioGroup rgBus;
    private RadioGroup rgDirection;
    private Spinner spinnerStop;
    private Button btnGetTiming;
    private LinearLayout layoutResult;
    private TextView txtResultStop, txtResultDirection, txtResultArrival, txtResultDeparture;
    private Button btnAnnounce, btnViewMap;

    private DatabaseHelper dbHelper;
    private FirestoreRepository firestoreRepo;
    private List<FirestoreRepository.FirestoreStop> stopList = new ArrayList<>();
    private ArrayAdapter<FirestoreRepository.FirestoreStop> spinnerAdapter;

    private ListenerRegistration stopsListener;
    private ListenerRegistration scheduleListener;
    private TextToSpeech tts;

    // Holds the last queried schedule for TTS announcement
    private String lastAnnouncementText = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        setContentView(R.layout.activity_bus_schedule);

        dbHelper = new DatabaseHelper(this);
        firestoreRepo = FirestoreRepository.getInstance(this);
        if (firestoreRepo.isAvailable()) {
            firestoreRepo.seedDemoDataIfEmpty(null);
        }

        // ── Find views ──────────────────────────────────────────────
        rgSchedulePeriod = findViewById(R.id.rgSchedulePeriod);
        rbMorning = findViewById(R.id.rbMorning);
        rbEvening = findViewById(R.id.rbEvening);
        rgBus = findViewById(R.id.rgBus);
        rgDirection = findViewById(R.id.rgDirection);
        spinnerStop = findViewById(R.id.spinnerStop);
        btnGetTiming = findViewById(R.id.btnGetTiming);
        layoutResult = findViewById(R.id.layoutResult);
        txtResultStop = findViewById(R.id.txtResultStop);
        txtResultDirection = findViewById(R.id.txtResultDirection);
        txtResultArrival = findViewById(R.id.txtResultArrival);
        txtResultDeparture = findViewById(R.id.txtResultDeparture);
        btnAnnounce = findViewById(R.id.btnAnnounce);
        btnViewMap = findViewById(R.id.btnViewMap);

        loadPeriods();

        // Setup Spinner adapter
        spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, stopList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStop.setAdapter(spinnerAdapter);

        // ── Populate Spinner with stops (Firestore with SQLite fallback) ──
        loadStops();

        // ── Initialize Text-to-Speech engine ────────────────────────
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                Log.d(TAG, "TTS initialized successfully.");
            } else {
                Log.e(TAG, "TTS initialization failed.");
            }
        });

        // ── Get Timing button ───────────────────────────────────────
        btnGetTiming.setOnClickListener(v -> fetchAndDisplaySchedule());

        // ── Announce button (TTS) ───────────────────────────────────
        btnAnnounce.setOnClickListener(v -> {
            if (lastAnnouncementText != null && tts != null) {
                tts.speak(lastAnnouncementText, TextToSpeech.QUEUE_FLUSH, null, "BusSathi_TTS");
            } else {
                Toast.makeText(this, "No timing to announce.", Toast.LENGTH_SHORT).show();
            }
        });

        // ── View Route on Map (Intent with geo URI) ─────────────────
        btnViewMap.setOnClickListener(v -> {
            try {
                Uri geoUri = Uri.parse("geo:0,0?q=college+bus+route");
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, geoUri);
                startActivity(mapIntent);
            } catch (Exception e) {
                Toast.makeText(this,
                        "No map application available or unable to open maps.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadPeriods() {
        if (firestoreRepo.isAvailable()) {
            firestoreRepo.getAllPeriods(new FirestoreRepository.Callback<List<FirestoreRepository.FirestorePeriod>>() {
                @Override
                public void onSuccess(List<FirestoreRepository.FirestorePeriod> periods) {
                    if (periods != null && !periods.isEmpty()) {
                        for (FirestoreRepository.FirestorePeriod p : periods) {
                            if ("morning".equalsIgnoreCase(p.id)) {
                                if (rbMorning != null) rbMorning.setText(p.getDisplayText());
                            } else if ("evening".equalsIgnoreCase(p.id)) {
                                if (rbEvening != null) rbEvening.setText(p.getDisplayText());
                            }
                        }
                    } else {
                        loadPeriodsFromSqlite();
                    }
                }

                @Override
                public void onError(Exception e) {
                    loadPeriodsFromSqlite();
                }
            });
        } else {
            loadPeriodsFromSqlite();
        }
    }

    private void loadPeriodsFromSqlite() {
        List<DatabaseHelper.SchedulePeriod> periods = dbHelper.getAllPeriods();
        for (DatabaseHelper.SchedulePeriod p : periods) {
            if ("morning".equalsIgnoreCase(p.id)) {
                if (rbMorning != null) rbMorning.setText(p.getDisplayText());
            } else if ("evening".equalsIgnoreCase(p.id)) {
                if (rbEvening != null) rbEvening.setText(p.getDisplayText());
            }
        }
    }

    /**
     * Load stops using Firestore real-time listener if available,
     * otherwise fall back to local SQLite Cursor read.
     */
    private void loadStops() {
        if (firestoreRepo.isAvailable()) {
            if (stopsListener != null) stopsListener.remove();

            stopsListener = firestoreRepo.listenToStops(new FirestoreRepository.Callback<List<FirestoreRepository.FirestoreStop>>() {
                @Override
                public void onSuccess(List<FirestoreRepository.FirestoreStop> stops) {
                    stopList.clear();
                    stopList.addAll(stops);
                    spinnerAdapter.notifyDataSetChanged();
                    Log.d(TAG, "Loaded " + stops.size() + " stops from Cloud Firestore.");
                }

                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "Firestore listen stops error: " + e.getMessage());
                    loadStopsFromSqlite();
                }
            });
        } else {
            loadStopsFromSqlite();
        }
    }

    private void loadStopsFromSqlite() {
        List<DatabaseHelper.Stop> localStops = dbHelper.getAllStops();
        stopList.clear();
        for (DatabaseHelper.Stop s : localStops) {
            stopList.add(new FirestoreRepository.FirestoreStop(String.valueOf(s.id), s.name, s.order));
        }
        spinnerAdapter.notifyDataSetChanged();
    }

    /**
     * Read the selected period, bus, direction and stop, retrieve timing from Firestore
     * (or SQLite fallback), and display the result.
     */
    private void fetchAndDisplaySchedule() {
        if (stopList == null || stopList.isEmpty()) {
            Toast.makeText(this, "No stops available.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Determine Schedule Period
        String schedulePeriod = (rgSchedulePeriod != null && rgSchedulePeriod.getCheckedRadioButtonId() == R.id.rbEvening)
                ? DatabaseHelper.PERIOD_EVENING : DatabaseHelper.PERIOD_MORNING;
        String periodLabel = DatabaseHelper.PERIOD_EVENING.equals(schedulePeriod) ? "Evening" : "Morning";

        // Determine Bus Number
        String busNumber = (rgBus != null && rgBus.getCheckedRadioButtonId() == R.id.rbBus2)
                ? DatabaseHelper.BUS_2 : DatabaseHelper.BUS_1;
        String busLabel = DatabaseHelper.BUS_2.equals(busNumber) ? "Bus 2" : "Bus 1";

        // Determine direction
        String direction;
        String directionLabel;
        if (rgDirection.getCheckedRadioButtonId() == R.id.rbToKathora) {
            direction = DatabaseHelper.DIR_TO_KATHORA;
            directionLabel = getString(R.string.dir_to_kathora);
        } else {
            direction = DatabaseHelper.DIR_TO_COLLEGE;
            directionLabel = getString(R.string.dir_to_college);
        }

        // Get selected stop
        int position = spinnerStop.getSelectedItemPosition();
        if (position < 0 || position >= stopList.size()) {
            Toast.makeText(this, "Please select a stop.", Toast.LENGTH_SHORT).show();
            return;
        }
        FirestoreRepository.FirestoreStop selectedStop = stopList.get(position);

        if (firestoreRepo.isAvailable()) {
            // Real-time listener for this bus, period, stop & direction in Firestore
            if (scheduleListener != null) scheduleListener.remove();

            scheduleListener = firestoreRepo.listenToSchedule(busNumber, schedulePeriod, selectedStop.id, direction,
                    new FirestoreRepository.Callback<FirestoreRepository.FirestoreSchedule>() {
                        @Override
                        public void onSuccess(FirestoreRepository.FirestoreSchedule schedule) {
                            if (schedule != null) {
                                displaySchedule(busLabel, periodLabel, selectedStop.stopName, directionLabel,
                                        schedule.arrivalTime, schedule.departureTime);
                            } else {
                                // Fallback to local SQLite if Firestore record is missing or not yet synced
                                boolean foundInSqlite = loadAndDisplayFromSqlite(busNumber, busLabel, schedulePeriod,
                                        periodLabel, selectedStop, direction, directionLabel);
                                if (!foundInSqlite) {
                                    hideSchedule();
                                    Toast.makeText(BusScheduleActivity.this,
                                            "Timing not available for this selection.",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            // Fallback to local SQLite on network/Firestore error
                            boolean foundInSqlite = loadAndDisplayFromSqlite(busNumber, busLabel, schedulePeriod,
                                     periodLabel, selectedStop, direction, directionLabel);
                            if (!foundInSqlite) {
                                hideSchedule();
                                Toast.makeText(BusScheduleActivity.this,
                                        "Timing not available for this selection.",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } else {
            // Local SQLite fallback (Unit 5)
            boolean foundInSqlite = loadAndDisplayFromSqlite(busNumber, busLabel, schedulePeriod,
                    periodLabel, selectedStop, direction, directionLabel);
            if (!foundInSqlite) {
                hideSchedule();
                Toast.makeText(this, "Timing not available for this selection.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean loadAndDisplayFromSqlite(String busNumber, String busLabel, String schedulePeriod,
                                             String periodLabel, FirestoreRepository.FirestoreStop selectedStop,
                                             String direction, String directionLabel) {
        int stopIdInt = -1;
        try {
            stopIdInt = Integer.parseInt(selectedStop.id);
        } catch (Exception ignored) {}
        if (stopIdInt == -1) {
            stopIdInt = dbHelper.getStopIdByName(selectedStop.stopName);
        }

        if (stopIdInt != -1) {
            DatabaseHelper.Schedule local = dbHelper.getSchedule(busNumber, schedulePeriod, stopIdInt, direction);
            if (local != null) {
                displaySchedule(busLabel, periodLabel, selectedStop.stopName, directionLabel,
                        local.arrivalTime, local.departureTime);
                // Proactively sync to Firestore in background
                if (firestoreRepo.isAvailable()) {
                    firestoreRepo.saveOrUpdateSchedule(busNumber, schedulePeriod, selectedStop.id,
                            direction, local.arrivalTime, local.departureTime, null);
                }
                return true;
            }
        }
        return false;
    }

    private void displaySchedule(String busLabel, String periodLabel,
                                  String stopName, String directionLabel,
                                  String arrivalTime, String departureTime) {
        txtResultStop.setText(stopName);
        txtResultDirection.setText(busLabel + " • " + periodLabel + " • " + directionLabel);
        txtResultArrival.setText(arrivalTime);
        txtResultDeparture.setText(departureTime);

        layoutResult.setVisibility(View.VISIBLE);
        btnAnnounce.setVisibility(View.VISIBLE);
        btnViewMap.setVisibility(View.VISIBLE);

        // Build TTS announcement text
        lastAnnouncementText = busLabel + " " + periodLabel + " schedule, " + directionLabel +
                ", arrives at " + stopName +
                " at " + arrivalTime +
                " and departs at " + departureTime + ".";
    }

    private void hideSchedule() {
        layoutResult.setVisibility(View.GONE);
        btnAnnounce.setVisibility(View.GONE);
        btnViewMap.setVisibility(View.GONE);
        lastAnnouncementText = null;
    }

    // ── Lifecycle: release listeners and TTS on destroy ──────────────
    @Override
    protected void onDestroy() {
        if (stopsListener != null) {
            stopsListener.remove();
            stopsListener = null;
        }
        if (scheduleListener != null) {
            scheduleListener.remove();
            scheduleListener = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPeriods();
        loadStops();
        Log.d(TAG, "onResume called");
    }
}
