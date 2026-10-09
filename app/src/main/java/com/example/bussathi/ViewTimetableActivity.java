package com.example.bussathi;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ViewTimetableActivity extends AppCompatActivity {

    private static final String TAG = "ViewTimetableActivity";

    private RadioGroup rgTimetableBus, rgTimetablePeriod;
    private RadioButton rbTimetableMorning, rbTimetableEvening;
    private ListView lvToKathora, lvToCollege;
    private DatabaseHelper dbHelper;
    private FirestoreRepository firestoreRepo;

    private ListenerRegistration listenerToKathora;
    private ListenerRegistration listenerToCollege;

    private Map<String, FirestoreRepository.FirestoreStop> stopMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        setContentView(R.layout.activity_view_timetable);

        dbHelper = new DatabaseHelper(this);
        firestoreRepo = FirestoreRepository.getInstance(this);

        rgTimetableBus = findViewById(R.id.rgTimetableBus);
        rgTimetablePeriod = findViewById(R.id.rgTimetablePeriod);
        rbTimetableMorning = findViewById(R.id.rbTimetableMorning);
        rbTimetableEvening = findViewById(R.id.rbTimetableEvening);
        lvToKathora = findViewById(R.id.lvTimetableToKathora);
        lvToCollege = findViewById(R.id.lvTimetableToCollege);

        loadPeriods();

        if (rgTimetableBus != null) {
            rgTimetableBus.setOnCheckedChangeListener((group, checkedId) -> loadTimetable());
        }
        if (rgTimetablePeriod != null) {
            rgTimetablePeriod.setOnCheckedChangeListener((group, checkedId) -> loadTimetable());
        }

        if (firestoreRepo.isAvailable()) {
            firestoreRepo.seedDemoDataIfEmpty(null);
        }

        loadTimetable();
    }

    private void loadPeriods() {
        if (firestoreRepo.isAvailable()) {
            firestoreRepo.getAllPeriods(new FirestoreRepository.Callback<List<FirestoreRepository.FirestorePeriod>>() {
                @Override
                public void onSuccess(List<FirestoreRepository.FirestorePeriod> periods) {
                    if (periods != null && !periods.isEmpty()) {
                        for (FirestoreRepository.FirestorePeriod p : periods) {
                            if ("morning".equalsIgnoreCase(p.id)) {
                                if (rbTimetableMorning != null) rbTimetableMorning.setText(p.getDisplayText());
                            } else if ("evening".equalsIgnoreCase(p.id)) {
                                if (rbTimetableEvening != null) rbTimetableEvening.setText(p.getDisplayText());
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
                if (rbTimetableMorning != null) rbTimetableMorning.setText(p.getDisplayText());
            } else if ("evening".equalsIgnoreCase(p.id)) {
                if (rbTimetableEvening != null) rbTimetableEvening.setText(p.getDisplayText());
            }
        }
    }

    private String getSelectedBus() {
        if (rgTimetableBus != null && rgTimetableBus.getCheckedRadioButtonId() == R.id.rbTimetableBus2) {
            return DatabaseHelper.BUS_2;
        }
        return DatabaseHelper.BUS_1;
    }

    private String getSelectedPeriod() {
        if (rgTimetablePeriod != null && rgTimetablePeriod.getCheckedRadioButtonId() == R.id.rbTimetableEvening) {
            return DatabaseHelper.PERIOD_EVENING;
        }
        return DatabaseHelper.PERIOD_MORNING;
    }

    private void loadTimetable() {
        if (firestoreRepo.isAvailable()) {
            // First load stops to map stopId -> stopName and stopOrder
            firestoreRepo.getAllStops(new FirestoreRepository.Callback<List<FirestoreRepository.FirestoreStop>>() {
                @Override
                public void onSuccess(List<FirestoreRepository.FirestoreStop> stops) {
                    stopMap.clear();
                    for (FirestoreRepository.FirestoreStop s : stops) {
                        stopMap.put(s.id, s);
                    }
                    listenToCloudSchedules();
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

    private void listenToCloudSchedules() {
        if (listenerToKathora != null) listenerToKathora.remove();
        if (listenerToCollege != null) listenerToCollege.remove();

        String busNumber = getSelectedBus();
        String schedulePeriod = getSelectedPeriod();

        // Listen for TO_KATHORA schedules
        listenerToKathora = firestoreRepo.listenToSchedules(busNumber, schedulePeriod,
                DatabaseHelper.DIR_TO_KATHORA,
                new FirestoreRepository.Callback<List<FirestoreRepository.FirestoreSchedule>>() {
                    @Override
                    public void onSuccess(List<FirestoreRepository.FirestoreSchedule> list) {
                        if (list == null || list.isEmpty()) {
                            loadToKathoraFromSqlite(busNumber, schedulePeriod);
                            return;
                        }
                        populateScheduleStopNamesAndSort(list);
                        ScheduleAdapter adapterK = new ScheduleAdapter(list);
                        lvToKathora.setAdapter(adapterK);
                        RouteActivity.setListViewHeightBasedOnChildren(lvToKathora);
                    }

                    @Override
                    public void onError(Exception e) {
                        loadToKathoraFromSqlite(busNumber, schedulePeriod);
                    }
                });

        // Listen for TO_COLLEGE schedules
        listenerToCollege = firestoreRepo.listenToSchedules(busNumber, schedulePeriod,
                DatabaseHelper.DIR_TO_COLLEGE,
                new FirestoreRepository.Callback<List<FirestoreRepository.FirestoreSchedule>>() {
                    @Override
                    public void onSuccess(List<FirestoreRepository.FirestoreSchedule> list) {
                        if (list == null || list.isEmpty()) {
                            loadToCollegeFromSqlite(busNumber, schedulePeriod);
                            return;
                        }
                        populateScheduleStopNamesAndSort(list);
                        ScheduleAdapter adapterC = new ScheduleAdapter(list);
                        lvToCollege.setAdapter(adapterC);
                        RouteActivity.setListViewHeightBasedOnChildren(lvToCollege);
                    }

                    @Override
                    public void onError(Exception e) {
                        loadToCollegeFromSqlite(busNumber, schedulePeriod);
                    }
                });
    }

    private void loadToKathoraFromSqlite(String busNumber, String schedulePeriod) {
        List<DatabaseHelper.Schedule> toKathora =
                dbHelper.getSchedules(busNumber, schedulePeriod, DatabaseHelper.DIR_TO_KATHORA);
        List<FirestoreRepository.FirestoreSchedule> listK = new ArrayList<>();
        for (DatabaseHelper.Schedule sc : toKathora) {
            FirestoreRepository.FirestoreSchedule f = new FirestoreRepository.FirestoreSchedule(
                    String.valueOf(sc.id), busNumber, schedulePeriod, String.valueOf(sc.stopId), sc.direction, sc.arrivalTime, sc.departureTime);
            f.stopName = sc.stopName;
            listK.add(f);
        }
        ScheduleAdapter adapterK = new ScheduleAdapter(listK);
        lvToKathora.setAdapter(adapterK);
        RouteActivity.setListViewHeightBasedOnChildren(lvToKathora);
    }

    private void loadToCollegeFromSqlite(String busNumber, String schedulePeriod) {
        List<DatabaseHelper.Schedule> toCollege =
                dbHelper.getSchedules(busNumber, schedulePeriod, DatabaseHelper.DIR_TO_COLLEGE);
        List<FirestoreRepository.FirestoreSchedule> listC = new ArrayList<>();
        for (DatabaseHelper.Schedule sc : toCollege) {
            FirestoreRepository.FirestoreSchedule f = new FirestoreRepository.FirestoreSchedule(
                    String.valueOf(sc.id), busNumber, schedulePeriod, String.valueOf(sc.stopId), sc.direction, sc.arrivalTime, sc.departureTime);
            f.stopName = sc.stopName;
            listC.add(f);
        }
        ScheduleAdapter adapterC = new ScheduleAdapter(listC);
        lvToCollege.setAdapter(adapterC);
        RouteActivity.setListViewHeightBasedOnChildren(lvToCollege);
    }

    private void populateScheduleStopNamesAndSort(List<FirestoreRepository.FirestoreSchedule> list) {
        for (FirestoreRepository.FirestoreSchedule s : list) {
            FirestoreRepository.FirestoreStop st = stopMap.get(s.stopId);
            if (st != null) {
                s.stopName = st.stopName;
            } else {
                s.stopName = "Stop";
            }
        }

        // Sort by stopOrder
        Collections.sort(list, (a, b) -> {
            FirestoreRepository.FirestoreStop stA = stopMap.get(a.stopId);
            FirestoreRepository.FirestoreStop stB = stopMap.get(b.stopId);
            int orderA = stA != null ? stA.stopOrder : 0;
            int orderB = stB != null ? stB.stopOrder : 0;
            return Integer.compare(orderA, orderB);
        });
    }

    private void loadFromSqlite() {
        String busNumber = getSelectedBus();
        String schedulePeriod = getSelectedPeriod();

        // TO_KATHORA schedules
        List<DatabaseHelper.Schedule> toKathora =
                dbHelper.getSchedules(busNumber, schedulePeriod, DatabaseHelper.DIR_TO_KATHORA);
        List<FirestoreRepository.FirestoreSchedule> listK = new ArrayList<>();
        for (DatabaseHelper.Schedule sc : toKathora) {
            FirestoreRepository.FirestoreSchedule f = new FirestoreRepository.FirestoreSchedule(
                    String.valueOf(sc.id), busNumber, schedulePeriod, String.valueOf(sc.stopId), sc.direction, sc.arrivalTime, sc.departureTime);
            f.stopName = sc.stopName;
            listK.add(f);
        }
        ScheduleAdapter adapterK = new ScheduleAdapter(listK);
        lvToKathora.setAdapter(adapterK);
        RouteActivity.setListViewHeightBasedOnChildren(lvToKathora);

        // TO_COLLEGE schedules
        List<DatabaseHelper.Schedule> toCollege =
                dbHelper.getSchedules(busNumber, schedulePeriod, DatabaseHelper.DIR_TO_COLLEGE);
        List<FirestoreRepository.FirestoreSchedule> listC = new ArrayList<>();
        for (DatabaseHelper.Schedule sc : toCollege) {
            FirestoreRepository.FirestoreSchedule f = new FirestoreRepository.FirestoreSchedule(
                    String.valueOf(sc.id), busNumber, schedulePeriod, String.valueOf(sc.stopId), sc.direction, sc.arrivalTime, sc.departureTime);
            f.stopName = sc.stopName;
            listC.add(f);
        }
        ScheduleAdapter adapterC = new ScheduleAdapter(listC);
        lvToCollege.setAdapter(adapterC);
        RouteActivity.setListViewHeightBasedOnChildren(lvToCollege);
    }

    /**
     * Custom adapter for schedule entries.
     */
    private class ScheduleAdapter extends BaseAdapter {
        private final List<FirestoreRepository.FirestoreSchedule> schedules;

        ScheduleAdapter(List<FirestoreRepository.FirestoreSchedule> schedules) {
            this.schedules = schedules;
        }

        @Override public int getCount() { return schedules.size(); }
        @Override public Object getItem(int pos) { return schedules.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_schedule, parent, false);
            }
            TextView txtStop = convertView.findViewById(R.id.txtSchedStopName);
            TextView txtArr = convertView.findViewById(R.id.txtSchedArrival);
            TextView txtDep = convertView.findViewById(R.id.txtSchedDeparture);

            FirestoreRepository.FirestoreSchedule sc = schedules.get(position);
            txtStop.setText(sc.stopName != null ? sc.stopName : "Stop");
            txtArr.setText(sc.arrivalTime);
            txtDep.setText(sc.departureTime);
            return convertView;
        }
    }

    @Override
    protected void onDestroy() {
        if (listenerToKathora != null) {
            listenerToKathora.remove();
            listenerToKathora = null;
        }
        if (listenerToCollege != null) {
            listenerToCollege.remove();
            listenerToCollege = null;
        }
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPeriods();
        if (!firestoreRepo.isAvailable()) {
            loadFromSqlite();
        }
        Log.d(TAG, "onResume called");
    }
}
