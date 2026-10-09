package com.example.bussathi;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;


public class MyStopActivity extends AppCompatActivity {

    private static final String TAG = "MyStopActivity";
    private static final String PREFS_NAME = "BusSathiPrefs";
    private static final String KEY_MY_STOP_ID_STR = "my_stop_id_str";
    private static final String KEY_MY_STOP_ID = "my_stop_id"; // legacy int key

    private TextView txtSavedStopName, txtNextTiming, txtReturnTiming;
    private Spinner spinnerMyStop;
    private Button btnSaveMyStop;

    private DatabaseHelper dbHelper;
    private FirestoreRepository firestoreRepo;
    private List<FirestoreRepository.FirestoreStop> stopList = new ArrayList<>();
    private ArrayAdapter<FirestoreRepository.FirestoreStop> spinnerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        setContentView(R.layout.activity_my_stop);

        dbHelper = new DatabaseHelper(this);
        firestoreRepo = FirestoreRepository.getInstance(this);

        txtSavedStopName = findViewById(R.id.txtSavedStopName);
        txtNextTiming = findViewById(R.id.txtNextTiming);
        txtReturnTiming = findViewById(R.id.txtReturnTiming);
        spinnerMyStop = findViewById(R.id.spinnerMyStop);
        btnSaveMyStop = findViewById(R.id.btnSaveMyStop);

        spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, stopList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMyStop.setAdapter(spinnerAdapter);

        // Load stops into Spinner
        loadStops();

        // Display saved stop info
        displaySavedStop();

        // Save button
        btnSaveMyStop.setOnClickListener(v -> saveMyStop());
    }

    private void loadStops() {
        if (firestoreRepo.isAvailable()) {
            firestoreRepo.getAllStops(new FirestoreRepository.Callback<List<FirestoreRepository.FirestoreStop>>() {
                @Override
                public void onSuccess(List<FirestoreRepository.FirestoreStop> stops) {
                    stopList.clear();
                    stopList.addAll(stops);
                    spinnerAdapter.notifyDataSetChanged();
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
    }

    /**
     * Save the selected stop's ID and name to SharedPreferences.
     */
    private void saveMyStop() {
        if (stopList == null || stopList.isEmpty()) {
            Toast.makeText(this, "No stops available.", Toast.LENGTH_SHORT).show();
            return;
        }
        int position = spinnerMyStop.getSelectedItemPosition();
        if (position < 0 || position >= stopList.size()) return;

        FirestoreRepository.FirestoreStop selected = stopList.get(position);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_MY_STOP_ID_STR, selected.id);
        editor.putString("saved_stop_name", selected.stopName);
        editor.apply();

        Toast.makeText(this, "Saved: " + selected.stopName, Toast.LENGTH_SHORT).show();
        displaySavedStop();
    }

    /**
     * Read saved stop from SharedPreferences, query Firestore (or SQLite), and display.
     */
    private void displaySavedStop() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedStopIdStr = prefs.getString(KEY_MY_STOP_ID_STR, null);
        String savedStopName = prefs.getString("saved_stop_name", null);

        if (savedStopIdStr == null) {
            // Check legacy int key if present
            int legacyId = prefs.getInt(KEY_MY_STOP_ID, -1);
            if (legacyId != -1) {
                savedStopIdStr = String.valueOf(legacyId);
            }
        }

        if (savedStopIdStr == null) {
            txtSavedStopName.setText(getString(R.string.lbl_no_stop_saved));
            txtNextTiming.setText("--");
            txtReturnTiming.setText("--");
            return;
        }

        final String stopId = savedStopIdStr;
        if (savedStopName != null) {
            txtSavedStopName.setText(savedStopName);
        }

        if (firestoreRepo.isAvailable()) {
            // Fetch TO_KATHORA timing from Firestore
            firestoreRepo.getSchedule(stopId, DatabaseHelper.DIR_TO_KATHORA,
                    new FirestoreRepository.Callback<FirestoreRepository.FirestoreSchedule>() {
                        @Override
                        public void onSuccess(FirestoreRepository.FirestoreSchedule schedule) {
                            if (schedule != null) {
                                txtNextTiming.setText(schedule.arrivalTime);
                            } else {
                                txtNextTiming.setText("No timing set");
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            txtNextTiming.setText("--");
                        }
                    });

            // Fetch TO_COLLEGE timing from Firestore
            firestoreRepo.getSchedule(stopId, DatabaseHelper.DIR_TO_COLLEGE,
                    new FirestoreRepository.Callback<FirestoreRepository.FirestoreSchedule>() {
                        @Override
                        public void onSuccess(FirestoreRepository.FirestoreSchedule schedule) {
                            if (schedule != null) {
                                txtReturnTiming.setText(schedule.arrivalTime);
                            } else {
                                txtReturnTiming.setText("No timing set");
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            txtReturnTiming.setText("--");
                        }
                    });
        } else {
            // SQLite fallback
            try {
                int idInt = Integer.parseInt(stopId);
                DatabaseHelper.Stop stop = dbHelper.getStopById(idInt);
                if (stop != null) {
                    txtSavedStopName.setText(stop.name);
                    DatabaseHelper.Schedule toK = dbHelper.getSchedule(stop.id, DatabaseHelper.DIR_TO_KATHORA);
                    txtNextTiming.setText(toK != null ? toK.arrivalTime : "No timing set");

                    DatabaseHelper.Schedule toC = dbHelper.getSchedule(stop.id, DatabaseHelper.DIR_TO_COLLEGE);
                    txtReturnTiming.setText(toC != null ? toC.arrivalTime : "No timing set");
                }
            } catch (Exception e) {
                txtSavedStopName.setText(getString(R.string.lbl_no_stop_saved));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStops();
        displaySavedStop();
        Log.d(TAG, "onResume called");
    }
}
