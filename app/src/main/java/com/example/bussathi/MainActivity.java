package com.example.bussathi;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * MainActivity — home screen of BusSathi.
 *
 * Demonstrates:
 *   - Activity lifecycle (Unit 2/4)
 *   - Explicit Intents for navigation (Unit 2/4)
 *   - JSON data import on first launch (Unit 6)
 *   - XML layout with multiple Views (Unit 3)
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Button btnBusSchedule;
    private Button btnRoute;
    private Button btnMyStop;
    private Button btnAdmin;

    // ── Lifecycle: onCreate ─────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );
                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );
                    return insets;
                }
        );

        // ── Seed database from JSON on first launch (SQLite syllabus demo) ────
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        JsonImporter.importIfNeeded(this, dbHelper);

        // ── Seed Firebase Firestore demo data on first launch if empty ────────
        FirestoreRepository firestoreRepo = FirestoreRepository.getInstance(this);
        if (firestoreRepo.isAvailable()) {
            firestoreRepo.seedDemoDataIfEmpty(new FirestoreRepository.Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean seeded) {
                    if (seeded) Log.d(TAG, "Demo data seeded to Cloud Firestore.");
                }
                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "Firestore demo seed skipped/failed: " + e.getMessage());
                }
            });
        }

        // ── Wire up navigation buttons ──────────────────────────────
        btnBusSchedule = findViewById(R.id.btnBusSchedule);
        btnRoute = findViewById(R.id.btnRoute);
        btnMyStop = findViewById(R.id.btnMyStop);
        btnAdmin = findViewById(R.id.btnAdmin);

        // Explicit Intent → BusScheduleActivity
        btnBusSchedule.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BusScheduleActivity.class);
            startActivity(intent);
        });

        // Explicit Intent → RouteActivity
        btnRoute.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RouteActivity.class);
            startActivity(intent);
        });

        // Explicit Intent → MyStopActivity
        btnMyStop.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MyStopActivity.class);
            startActivity(intent);
        });

        // Explicit Intent → AdminLoginActivity
        btnAdmin.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AdminLoginActivity.class);
            startActivity(intent);
        });
    }

    // ── Lifecycle callbacks (for viva demonstration) ────────────────────
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
    }
}