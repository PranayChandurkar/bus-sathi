package com.example.bussathi;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class StopFormActivity extends AppCompatActivity {

    private static final String TAG = "StopFormActivity";

    private EditText etStopName, etStopOrder;
    private Button btnSave, btnCancel;
    private TextView txtTitle;

    private DatabaseHelper dbHelper;
    private FirestoreRepository firestoreRepo;

    private boolean isEditMode = false;
    private String editStopIdStr = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        setContentView(R.layout.activity_stop_form);

        dbHelper = new DatabaseHelper(this);
        firestoreRepo = FirestoreRepository.getInstance(this);

        txtTitle = findViewById(R.id.txtStopFormTitle);
        etStopName = findViewById(R.id.etStopName);
        etStopOrder = findViewById(R.id.etStopOrder);
        btnSave = findViewById(R.id.btnSaveStop);
        btnCancel = findViewById(R.id.btnCancelStop);

        // ── Check if editing an existing stop ───────────────────────
        if (getIntent().hasExtra("stop_id")) {
            isEditMode = true;
            Object idObj = getIntent().getExtras().get("stop_id");
            editStopIdStr = idObj != null ? String.valueOf(idObj) : null;

            String name = getIntent().getStringExtra("stop_name");
            int order = getIntent().getIntExtra("stop_order", 0);

            txtTitle.setText(getString(R.string.btn_edit_stop));
            if (name != null) etStopName.setText(name);
            etStopOrder.setText(String.valueOf(order));
            btnSave.setText(getString(R.string.lbl_update));
        }

        // ── Save / Update button ────────────────────────────────────
        btnSave.setOnClickListener(v -> saveStop());

        // ── Cancel button ───────────────────────────────────────────
        btnCancel.setOnClickListener(v -> finish());
    }

    /**
     * Validate input and perform Firestore INSERT or UPDATE.
     */
    private void saveStop() {
        String name = etStopName.getText().toString().trim();
        String orderStr = etStopOrder.getText().toString().trim();

        if (name.isEmpty()) {
            etStopName.setError("Stop name is required");
            return;
        }
        if (orderStr.isEmpty()) {
            etStopOrder.setError("Stop order is required");
            return;
        }

        int order;
        try {
            order = Integer.parseInt(orderStr);
        } catch (NumberFormatException e) {
            etStopOrder.setError("Enter a valid number");
            return;
        }

        if (isEditMode) {
            if (firestoreRepo.isAvailable() && editStopIdStr != null) {
                firestoreRepo.updateStop(editStopIdStr, name, order, new FirestoreRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Toast.makeText(StopFormActivity.this,
                                "Stop updated in Cloud Firestore.",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(StopFormActivity.this,
                                "Firestore update error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Also maintain local SQLite
            try {
                int localId = Integer.parseInt(editStopIdStr);
                dbHelper.updateStop(localId, name, order);
            } catch (Exception ignored) {}

            if (!firestoreRepo.isAvailable()) {
                Toast.makeText(this, "Stop updated locally.", Toast.LENGTH_SHORT).show();
            }
        } else {
            // ADD mode
            if (firestoreRepo.isAvailable()) {
                firestoreRepo.addStop(name, order, new FirestoreRepository.Callback<String>() {
                    @Override
                    public void onSuccess(String docId) {
                        Toast.makeText(StopFormActivity.this,
                                "Stop added to Cloud Firestore.",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(StopFormActivity.this,
                                "Firestore add error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Also maintain local SQLite
            dbHelper.addStop(name, order);

            if (!firestoreRepo.isAvailable()) {
                Toast.makeText(this, "Stop added locally.", Toast.LENGTH_SHORT).show();
            }
        }

        finish();
    }
}
