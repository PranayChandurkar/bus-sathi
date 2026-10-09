package com.example.bussathi;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AdminActivity extends AppCompatActivity {

    private static final String TAG = "AdminActivity";

    // Intent extra keys for StopListActivity mode
    public static final String EXTRA_MODE = "admin_mode";
    public static final String MODE_EDIT = "edit";
    public static final String MODE_DELETE = "delete";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        setContentView(R.layout.activity_admin);

        Button btnAddStop = findViewById(R.id.btnAddStop);
        Button btnEditStop = findViewById(R.id.btnEditStop);
        Button btnDeleteStop = findViewById(R.id.btnDeleteStop);
        Button btnManageTimings = findViewById(R.id.btnManageTimings);
        Button btnViewTimetable = findViewById(R.id.btnViewTimetable);
        Button btnLogout = findViewById(R.id.btnLogout);

        // ── Add Stop → StopFormActivity (no extras = add mode) ──────
        btnAddStop.setOnClickListener(v -> {
            Intent intent = new Intent(AdminActivity.this, StopFormActivity.class);
            startActivity(intent);
        });

        // ── Edit Stop → StopListActivity in edit mode ───────────────
        btnEditStop.setOnClickListener(v -> {
            Intent intent = new Intent(AdminActivity.this, StopListActivity.class);
            intent.putExtra(EXTRA_MODE, MODE_EDIT);
            startActivity(intent);
        });

        // ── Delete Stop → StopListActivity in delete mode ───────────
        btnDeleteStop.setOnClickListener(v -> {
            Intent intent = new Intent(AdminActivity.this, StopListActivity.class);
            intent.putExtra(EXTRA_MODE, MODE_DELETE);
            startActivity(intent);
        });

        // ── Manage Timings → ManageTimingsActivity ──────────────────
        btnManageTimings.setOnClickListener(v -> {
            Intent intent = new Intent(AdminActivity.this, ManageTimingsActivity.class);
            startActivity(intent);
        });

        // ── View Timetable → ViewTimetableActivity ──────────────────
        btnViewTimetable.setOnClickListener(v -> {
            Intent intent = new Intent(AdminActivity.this, ViewTimetableActivity.class);
            startActivity(intent);
        });

        // ── Logout → finish this activity ───────────────────────────
        btnLogout.setOnClickListener(v -> {
            Toast.makeText(this, "Logged out.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
    }
}
