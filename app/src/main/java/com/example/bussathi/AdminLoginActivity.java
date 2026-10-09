package com.example.bussathi;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AdminLoginActivity extends AppCompatActivity {

    private static final String TAG = "AdminLoginActivity";

    private EditText etUsername, etPassword;
    private Button btnLogin;
    private TextView txtLoginError;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        setContentView(R.layout.activity_admin_login);

        dbHelper = new DatabaseHelper(this);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        txtLoginError = findViewById(R.id.txtLoginError);

        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    /**
     * Validate credentials against SQLite admins table.
     */
    private void attemptLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            txtLoginError.setText("Please enter both username and password.");
            txtLoginError.setVisibility(android.view.View.VISIBLE);
            return;
        }

        boolean valid = dbHelper.checkAdminLogin(username, password);

        if (valid) {
            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
            // Navigate to Admin Dashboard
            Intent intent = new Intent(AdminLoginActivity.this, AdminActivity.class);
            startActivity(intent);
            finish(); // Close login screen
        } else {
            txtLoginError.setText("Invalid username or password.");
            txtLoginError.setVisibility(android.view.View.VISIBLE);
            Toast.makeText(this, "Login failed!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
    }
}
