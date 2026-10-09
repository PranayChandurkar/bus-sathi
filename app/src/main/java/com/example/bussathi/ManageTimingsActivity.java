package com.example.bussathi;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ManageTimingsActivity extends AppCompatActivity {

    private static final String TAG = "ManageTimingsActivity";

    private Spinner spinnerTimingStop;
    private RadioGroup rgTimingBus;
    private RadioGroup rgTimingSchedulePeriod;
    private RadioButton rbTimingMorning, rbTimingEvening;
    private RadioGroup rgTimingDirection;
    private Button btnSetArrival, btnSetDeparture, btnSaveSchedule, btnDeleteSchedule, btnEditPeriodHours;
    private TextView txtArrivalTime, txtDepartureTime, txtExistingSchedule;

    private DatabaseHelper dbHelper;
    private FirestoreRepository firestoreRepo;
    private List<FirestoreRepository.FirestoreStop> stopList = new ArrayList<>();
    private ArrayAdapter<FirestoreRepository.FirestoreStop> spinnerAdapter;

    private String selectedArrival = null;
    private String selectedDeparture = null;
    private String existingScheduleIdStr = null; // Firestore doc ID
    private int existingLocalScheduleId = -1; // SQLite ID fallback

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        setContentView(R.layout.activity_manage_timings);

        dbHelper = new DatabaseHelper(this);
        firestoreRepo = FirestoreRepository.getInstance(this);

        spinnerTimingStop = findViewById(R.id.spinnerTimingStop);
        rgTimingBus = findViewById(R.id.rgTimingBus);
        rgTimingSchedulePeriod = findViewById(R.id.rgTimingSchedulePeriod);
        rbTimingMorning = findViewById(R.id.rbTimingMorning);
        rbTimingEvening = findViewById(R.id.rbTimingEvening);
        btnEditPeriodHours = findViewById(R.id.btnEditPeriodHours);
        rgTimingDirection = findViewById(R.id.rgTimingDirection);
        btnSetArrival = findViewById(R.id.btnSetArrival);
        btnSetDeparture = findViewById(R.id.btnSetDeparture);
        btnSaveSchedule = findViewById(R.id.btnSaveSchedule);
        btnDeleteSchedule = findViewById(R.id.btnDeleteSchedule);
        txtArrivalTime = findViewById(R.id.txtArrivalTime);
        txtDepartureTime = findViewById(R.id.txtDepartureTime);
        txtExistingSchedule = findViewById(R.id.txtExistingSchedule);

        spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, stopList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTimingStop.setAdapter(spinnerAdapter);

        loadPeriods();
        loadStops();

        // ── TimePickerDialog for Arrival ────────────────────────────
        btnSetArrival.setOnClickListener(v -> showTimePicker(true));

        // ── TimePickerDialog for Departure ──────────────────────────
        btnSetDeparture.setOnClickListener(v -> showTimePicker(false));

        // ── Edit Period Hours ───────────────────────────────────────
        if (btnEditPeriodHours != null) {
            btnEditPeriodHours.setOnClickListener(v -> showEditPeriodHoursDialog());
        }

        // ── Save schedule ───────────────────────────────────────────
        btnSaveSchedule.setOnClickListener(v -> saveSchedule());

        // ── Delete schedule ─────────────────────────────────────────
        btnDeleteSchedule.setOnClickListener(v -> confirmDeleteSchedule());

        // ── Bus change listener ─────────────────────────────────────
        if (rgTimingBus != null) {
            rgTimingBus.setOnCheckedChangeListener((group, checkedId) -> loadExistingSchedule());
        }

        // ── Schedule Period change listener ─────────────────────────
        if (rgTimingSchedulePeriod != null) {
            rgTimingSchedulePeriod.setOnCheckedChangeListener((group, checkedId) -> loadExistingSchedule());
        }

        // ── Direction change listener ───────────────────────────────
        rgTimingDirection.setOnCheckedChangeListener((group, checkedId) -> loadExistingSchedule());

        // ── Stop spinner selection listener ─────────────────────────
        spinnerTimingStop.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadExistingSchedule();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
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
                                if (rbTimingMorning != null) rbTimingMorning.setText(p.getDisplayText());
                            } else if ("evening".equalsIgnoreCase(p.id)) {
                                if (rbTimingEvening != null) rbTimingEvening.setText(p.getDisplayText());
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
                if (rbTimingMorning != null) rbTimingMorning.setText(p.getDisplayText());
            } else if ("evening".equalsIgnoreCase(p.id)) {
                if (rbTimingEvening != null) rbTimingEvening.setText(p.getDisplayText());
            }
        }
    }

    private void showEditPeriodHoursDialog() {
        String periodId = getSelectedPeriod();
        String periodName = DatabaseHelper.PERIOD_EVENING.equals(periodId) ? "Evening" : "Morning";

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog startDialog = new TimePickerDialog(this, (view1, startH, startM) -> {
            String startAmPm = startH >= 12 ? "PM" : "AM";
            int dispStartH = startH % 12;
            if (dispStartH == 0) dispStartH = 12;
            String startTime = String.format(Locale.US, "%02d:%02d %s", dispStartH, startM, startAmPm);

            TimePickerDialog endDialog = new TimePickerDialog(ManageTimingsActivity.this, (view2, endH, endM) -> {
                String endAmPm = endH >= 12 ? "PM" : "AM";
                int dispEndH = endH % 12;
                if (dispEndH == 0) dispEndH = 12;
                String endTime = String.format(Locale.US, "%02d:%02d %s", dispEndH, endM, endAmPm);

                // Update in SQLite
                dbHelper.updatePeriodTimes(periodId, startTime, endTime);

                // Update in Firestore
                if (firestoreRepo.isAvailable()) {
                    firestoreRepo.updatePeriodTimes(periodId, startTime, endTime, null);
                }

                loadPeriods();
                Toast.makeText(ManageTimingsActivity.this,
                        "Updated " + periodName + " hours (" + startTime + " – " + endTime + "). Stop timings remain unchanged.",
                        Toast.LENGTH_LONG).show();
            }, (dispStartH + 1) % 12 == 0 ? 12 : (dispStartH + 1) % 12, startM, false);
            endDialog.setTitle("Select " + periodName + " End Time");
            endDialog.show();
        }, hour, minute, false);
        startDialog.setTitle("Select " + periodName + " Start Time");
        startDialog.show();
    }

    private void loadStops() {
        if (firestoreRepo.isAvailable()) {
            firestoreRepo.getAllStops(new FirestoreRepository.Callback<List<FirestoreRepository.FirestoreStop>>() {
                @Override
                public void onSuccess(List<FirestoreRepository.FirestoreStop> stops) {
                    stopList.clear();
                    stopList.addAll(stops);
                    spinnerAdapter.notifyDataSetChanged();
                    if (!stopList.isEmpty()) {
                        loadExistingSchedule();
                    }
                }

                @Override
                public void onError(Exception e) {
                    loadStopsFromSqlite();
                }
            });
        } else {
            loadStopsFromSqlite();
        }
    }

    private void loadStopsFromSqlite() {
        List<DatabaseHelper.Stop> local = dbHelper.getAllStops();
        stopList.clear();
        for (DatabaseHelper.Stop s : local) {
            stopList.add(new FirestoreRepository.FirestoreStop(String.valueOf(s.id), s.name, s.order));
        }
        spinnerAdapter.notifyDataSetChanged();
        if (!stopList.isEmpty()) {
            loadExistingSchedule();
        }
    }

    /**
     * Show TimePickerDialog and format result as "hh:mm AM/PM".
     */
    private void showTimePicker(boolean isArrival) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, min) -> {
            String amPm = hourOfDay >= 12 ? "PM" : "AM";
            int displayHour = hourOfDay % 12;
            if (displayHour == 0) displayHour = 12;
            String formatted = String.format(Locale.US, "%02d:%02d %s", displayHour, min, amPm);

            if (isArrival) {
                selectedArrival = formatted;
                txtArrivalTime.setText(formatted);
            } else {
                selectedDeparture = formatted;
                txtDepartureTime.setText(formatted);
            }
        }, hour, minute, false);
        dialog.show();
    }

    private String getSelectedBus() {
        if (rgTimingBus != null && rgTimingBus.getCheckedRadioButtonId() == R.id.rbTimingBus2) {
            return DatabaseHelper.BUS_2;
        }
        return DatabaseHelper.BUS_1;
    }

    private String getSelectedPeriod() {
        if (rgTimingSchedulePeriod != null && rgTimingSchedulePeriod.getCheckedRadioButtonId() == R.id.rbTimingEvening) {
            return DatabaseHelper.PERIOD_EVENING;
        }
        return DatabaseHelper.PERIOD_MORNING;
    }

    private String getSelectedDirection() {
        if (rgTimingDirection.getCheckedRadioButtonId() == R.id.rbTimingToKathora) {
            return DatabaseHelper.DIR_TO_KATHORA;
        } else {
            return DatabaseHelper.DIR_TO_COLLEGE;
        }
    }

    /**
     * Load existing schedule for the current bus + period + stop + direction.
     */
    private void loadExistingSchedule() {
        if (stopList == null || stopList.isEmpty()) return;
        int pos = spinnerTimingStop.getSelectedItemPosition();
        if (pos < 0 || pos >= stopList.size()) return;

        FirestoreRepository.FirestoreStop stop = stopList.get(pos);
        String busNumber = getSelectedBus();
        String schedulePeriod = getSelectedPeriod();
        String direction = getSelectedDirection();
        String busLabel = DatabaseHelper.BUS_2.equals(busNumber) ? "Bus 2" : "Bus 1";
        String periodLabel = DatabaseHelper.PERIOD_EVENING.equals(schedulePeriod) ? "Evening" : "Morning";

        if (firestoreRepo.isAvailable()) {
            firestoreRepo.getSchedule(busNumber, schedulePeriod, stop.id, direction,
                    new FirestoreRepository.Callback<FirestoreRepository.FirestoreSchedule>() {
                        @Override
                        public void onSuccess(FirestoreRepository.FirestoreSchedule existing) {
                            if (existing != null) {
                                existingScheduleIdStr = existing.id;
                                selectedArrival = existing.arrivalTime;
                                selectedDeparture = existing.departureTime;
                                txtArrivalTime.setText(existing.arrivalTime);
                                txtDepartureTime.setText(existing.departureTime);
                                txtExistingSchedule.setText("Cloud schedule found for " + busLabel + " (" + periodLabel + "). Saving will update it.");
                                btnDeleteSchedule.setVisibility(View.VISIBLE);
                                btnSaveSchedule.setText("Update Timing");
                            } else {
                                resetScheduleFields("No Cloud schedule for " + busLabel + " (" + periodLabel + "). A new entry will be created.");
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            resetScheduleFields("Unable to check Cloud schedule: " + e.getMessage());
                        }
                    });
        } else {
            // Local SQLite fallback
            try {
                int localStopId = Integer.parseInt(stop.id);
                DatabaseHelper.Schedule existing = dbHelper.getSchedule(busNumber, schedulePeriod, localStopId, direction);
                if (existing != null) {
                    existingLocalScheduleId = existing.id;
                    selectedArrival = existing.arrivalTime;
                    selectedDeparture = existing.departureTime;
                    txtArrivalTime.setText(existing.arrivalTime);
                    txtDepartureTime.setText(existing.departureTime);
                    txtExistingSchedule.setText("Local schedule found for " + busLabel + " (" + periodLabel + "). Saving will update it.");
                    btnDeleteSchedule.setVisibility(View.VISIBLE);
                    btnSaveSchedule.setText("Update Timing");
                } else {
                    resetScheduleFields("No local schedule for " + busLabel + " (" + periodLabel + "). A new entry will be created.");
                }
            } catch (Exception e) {
                resetScheduleFields("Select timing");
            }
        }
    }

    private void resetScheduleFields(String notice) {
        existingScheduleIdStr = null;
        existingLocalScheduleId = -1;
        selectedArrival = null;
        selectedDeparture = null;
        txtArrivalTime.setText("--:-- --");
        txtDepartureTime.setText("--:-- --");
        txtExistingSchedule.setText(notice);
        btnDeleteSchedule.setVisibility(View.GONE);
        btnSaveSchedule.setText("Save Timing");
    }

    /**
     * Save or update schedule entry in Firestore (and SQLite).
     */
    private void saveSchedule() {
        if (stopList == null || stopList.isEmpty()) {
            Toast.makeText(this, "No stops available.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedArrival == null || selectedDeparture == null) {
            Toast.makeText(this, "Please set both arrival and departure times.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int pos = spinnerTimingStop.getSelectedItemPosition();
        FirestoreRepository.FirestoreStop stop = stopList.get(pos);
        String busNumber = getSelectedBus();
        String schedulePeriod = getSelectedPeriod();
        String direction = getSelectedDirection();

        if (firestoreRepo.isAvailable()) {
            firestoreRepo.saveOrUpdateSchedule(busNumber, schedulePeriod, stop.id, direction,
                    selectedArrival, selectedDeparture,
                    new FirestoreRepository.Callback<String>() {
                        @Override
                        public void onSuccess(String scheduleId) {
                            Toast.makeText(ManageTimingsActivity.this,
                                    "Timing saved to Cloud Firestore.", Toast.LENGTH_SHORT).show();
                            loadExistingSchedule();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(ManageTimingsActivity.this,
                                    "Save error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        // Also update local SQLite if matching stop exists
        try {
            int localStopId = dbHelper.getStopIdByName(stop.stopName);
            if (localStopId != -1) {
                DatabaseHelper.Schedule localSched = dbHelper.getSchedule(busNumber, schedulePeriod, localStopId, direction);
                if (localSched != null) {
                    dbHelper.updateSchedule(localSched.id, selectedArrival, selectedDeparture);
                } else {
                    dbHelper.addSchedule(busNumber, schedulePeriod, localStopId, direction, selectedArrival, selectedDeparture);
                }
            }
        } catch (Exception ignored) {}

        if (!firestoreRepo.isAvailable()) {
            Toast.makeText(this, "Timing saved locally.", Toast.LENGTH_SHORT).show();
            loadExistingSchedule();
        }
    }

    /**
     * Confirm and delete the current schedule entry.
     */
    private void confirmDeleteSchedule() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Timing")
                .setMessage("Are you sure you want to delete this timing entry?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (firestoreRepo.isAvailable() && existingScheduleIdStr != null) {
                        firestoreRepo.deleteSchedule(existingScheduleIdStr,
                                new FirestoreRepository.Callback<Void>() {
                                    @Override
                                    public void onSuccess(Void result) {
                                        Toast.makeText(ManageTimingsActivity.this,
                                                "Timing deleted from Cloud Firestore.",
                                                Toast.LENGTH_SHORT).show();
                                        loadExistingSchedule();
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        Toast.makeText(ManageTimingsActivity.this,
                                                "Delete error: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }

                    // Also delete local if applicable
                    if (existingLocalScheduleId != -1) {
                        dbHelper.deleteSchedule(existingLocalScheduleId);
                    }

                    if (!firestoreRepo.isAvailable()) {
                        Toast.makeText(this, "Timing deleted locally.", Toast.LENGTH_SHORT).show();
                        loadExistingSchedule();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPeriods();
        loadStops();
        Log.d(TAG, "onResume called");
    }
}
