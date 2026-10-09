package com.example.bussathi;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class StopListActivity extends AppCompatActivity {

    private static final String TAG = "StopListActivity";

    private EditText etSearchStop;
    private ListView lvStops;
    private TextView txtTitle;

    private DatabaseHelper dbHelper;
    private FirestoreRepository firestoreRepo;
    private ListenerRegistration stopsListener;

    private String mode;
    private List<FirestoreRepository.FirestoreStop> allStops = new ArrayList<>();
    private List<FirestoreRepository.FirestoreStop> filteredStops = new ArrayList<>();
    private StopAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        setContentView(R.layout.activity_stop_list);

        dbHelper = new DatabaseHelper(this);
        firestoreRepo = FirestoreRepository.getInstance(this);

        mode = getIntent().getStringExtra(AdminActivity.EXTRA_MODE);
        if (mode == null) mode = AdminActivity.MODE_EDIT;

        txtTitle = findViewById(R.id.txtStopListTitle);
        etSearchStop = findViewById(R.id.etSearchStop);
        lvStops = findViewById(R.id.lvStops);

        if (AdminActivity.MODE_DELETE.equals(mode)) {
            txtTitle.setText("Delete Stop");
        } else {
            txtTitle.setText("Edit Stop");
        }

        loadAndListenToStops();

        // Search filter
        etSearchStop.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s.toString().trim());
            }
        });

        // Item click handler
        lvStops.setOnItemClickListener((parent, view, position, id) -> {
            FirestoreRepository.FirestoreStop stop = filteredStops.get(position);
            if (AdminActivity.MODE_DELETE.equals(mode)) {
                confirmDelete(stop);
            } else {
                // Open edit form
                Intent intent = new Intent(StopListActivity.this, StopFormActivity.class);
                intent.putExtra("stop_id", stop.id);
                intent.putExtra("stop_name", stop.stopName);
                intent.putExtra("stop_order", stop.stopOrder);
                startActivity(intent);
            }
        });
    }

    private void loadAndListenToStops() {
        if (firestoreRepo.isAvailable()) {
            if (stopsListener != null) stopsListener.remove();

            stopsListener = firestoreRepo.listenToStops(new FirestoreRepository.Callback<List<FirestoreRepository.FirestoreStop>>() {
                @Override
                public void onSuccess(List<FirestoreRepository.FirestoreStop> stops) {
                    allStops = stops;
                    applyFilter(etSearchStop.getText().toString().trim());
                }

                @Override
                public void onError(Exception e) {
                    loadFromSqlite();
                }
            });
        } else {
            loadFromSqlite();
        }
    }

    private void loadFromSqlite() {
        List<DatabaseHelper.Stop> local = dbHelper.getAllStops();
        allStops.clear();
        for (DatabaseHelper.Stop s : local) {
            allStops.add(new FirestoreRepository.FirestoreStop(String.valueOf(s.id), s.name, s.order));
        }
        applyFilter(etSearchStop.getText().toString().trim());
    }

    private void applyFilter(String filter) {
        filteredStops = new ArrayList<>();
        for (FirestoreRepository.FirestoreStop s : allStops) {
            if (filter.isEmpty() || s.stopName.toLowerCase().contains(filter.toLowerCase())) {
                filteredStops.add(s);
            }
        }
        adapter = new StopAdapter(filteredStops);
        lvStops.setAdapter(adapter);
    }

    /**
     * Show confirmation dialog before deleting a stop and its schedules.
     */
    private void confirmDelete(FirestoreRepository.FirestoreStop stop) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Stop")
                .setMessage("Delete \"" + stop.stopName + "\" and all its schedule entries from Cloud database?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (firestoreRepo.isAvailable()) {
                        firestoreRepo.deleteStop(stop.id, new FirestoreRepository.Callback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                Toast.makeText(StopListActivity.this,
                                        stop.stopName + " deleted from Cloud Firestore.",
                                        Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(Exception e) {
                                Toast.makeText(StopListActivity.this,
                                        "Delete error: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    // Also maintain local SQLite consistency if matching stop exists
                    try {
                        int localId = dbHelper.getStopIdByName(stop.stopName);
                        if (localId != -1) {
                            dbHelper.deleteStop(localId);
                        }
                    } catch (Exception ignored) {}

                    if (!firestoreRepo.isAvailable()) {
                        loadFromSqlite();
                        Toast.makeText(this, stop.stopName + " deleted locally.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Custom adapter for the admin stop list.
     */
    private class StopAdapter extends BaseAdapter {
        private final List<FirestoreRepository.FirestoreStop> stops;

        StopAdapter(List<FirestoreRepository.FirestoreStop> stops) {
            this.stops = stops;
        }

        @Override public int getCount() { return stops.size(); }
        @Override public Object getItem(int pos) { return stops.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_admin_stop, parent, false);
            }
            TextView txtName = convertView.findViewById(R.id.txtAdminStopName);
            TextView txtOrder = convertView.findViewById(R.id.txtAdminStopOrder);

            FirestoreRepository.FirestoreStop stop = stops.get(position);
            txtName.setText(stop.stopName);
            txtOrder.setText("Order: " + stop.stopOrder);
            return convertView;
        }
    }

    @Override
    protected void onDestroy() {
        if (stopsListener != null) {
            stopsListener.remove();
            stopsListener = null;
        }
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!firestoreRepo.isAvailable()) {
            loadFromSqlite();
        }
        Log.d(TAG, "onResume called");
    }
}
