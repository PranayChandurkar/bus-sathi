package com.example.bussathi;

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

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RouteActivity extends AppCompatActivity {

    private static final String TAG = "RouteActivity";

    private EditText etSearch;
    private ListView lvForward, lvReturn;

    private DatabaseHelper dbHelper;
    private FirestoreRepository firestoreRepo;
    private ListenerRegistration stopsListener;

    private List<FirestoreRepository.FirestoreStop> allStops = new ArrayList<>();
    private List<FirestoreRepository.FirestoreStop> filteredStops = new ArrayList<>();
    private RouteStopAdapter forwardAdapter;
    private RouteStopAdapter returnAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        setContentView(R.layout.activity_route);

        dbHelper = new DatabaseHelper(this);
        firestoreRepo = FirestoreRepository.getInstance(this);

        etSearch = findViewById(R.id.etSearch);
        lvForward = findViewById(R.id.lvForwardRoute);
        lvReturn = findViewById(R.id.lvReturnRoute);

        loadAndListenToRoute();

        // ── Search / filter with TextWatcher ────────────────────────
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s.toString().trim());
            }
        });
    }

    /**
     * Listen to Firestore in real time, or fall back to SQLite.
     */
    private void loadAndListenToRoute() {
        if (firestoreRepo.isAvailable()) {
            if (stopsListener != null) stopsListener.remove();

            stopsListener = firestoreRepo.listenToStops(new FirestoreRepository.Callback<List<FirestoreRepository.FirestoreStop>>() {
                @Override
                public void onSuccess(List<FirestoreRepository.FirestoreStop> stops) {
                    allStops = stops;
                    applyFilter(etSearch.getText().toString().trim());
                    Log.d(TAG, "RouteActivity received " + stops.size() + " stops from Firestore.");
                }

                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "RouteActivity Firestore error: " + e.getMessage());
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
        applyFilter(etSearch.getText().toString().trim());
    }

    /**
     * Filter stops by query and update forward & return lists.
     */
    private void applyFilter(String filter) {
        filteredStops = new ArrayList<>();
        for (FirestoreRepository.FirestoreStop stop : allStops) {
            if (filter.isEmpty() || stop.stopName.toLowerCase().contains(filter.toLowerCase())) {
                filteredStops.add(stop);
            }
        }

        // Forward list
        forwardAdapter = new RouteStopAdapter(filteredStops);
        lvForward.setAdapter(forwardAdapter);
        setListViewHeightBasedOnChildren(lvForward);

        // Return list (dynamically reversed)
        List<FirestoreRepository.FirestoreStop> reversed = new ArrayList<>(filteredStops);
        Collections.reverse(reversed);
        returnAdapter = new RouteStopAdapter(reversed);
        lvReturn.setAdapter(returnAdapter);
        setListViewHeightBasedOnChildren(lvReturn);
    }

    /**
     * Custom adapter for displaying route stops with ↓ arrows.
     */
    private class RouteStopAdapter extends BaseAdapter {
        private final List<FirestoreRepository.FirestoreStop> stops;

        RouteStopAdapter(List<FirestoreRepository.FirestoreStop> stops) {
            this.stops = stops;
        }

        @Override public int getCount() { return stops.size(); }
        @Override public Object getItem(int pos) { return stops.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_route_stop, parent, false);
            }
            TextView txtName = convertView.findViewById(R.id.txtStopName);
            TextView txtArrow = convertView.findViewById(R.id.txtArrow);

            FirestoreRepository.FirestoreStop stop = stops.get(position);
            txtName.setText(stop.stopName);

            // Hide arrow on last item
            if (position == stops.size() - 1) {
                txtArrow.setVisibility(View.GONE);
            } else {
                txtArrow.setVisibility(View.VISIBLE);
            }
            return convertView;
        }
    }

    /**
     * Utility – set ListView height manually inside ScrollView.
     */
    public static void setListViewHeightBasedOnChildren(ListView listView) {
        BaseAdapter adapter = (BaseAdapter) listView.getAdapter();
        if (adapter == null) return;
        int totalHeight = 0;
        for (int i = 0; i < adapter.getCount(); i++) {
            View item = adapter.getView(i, null, listView);
            item.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            totalHeight += item.getMeasuredHeight();
        }
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (adapter.getCount() - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
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
