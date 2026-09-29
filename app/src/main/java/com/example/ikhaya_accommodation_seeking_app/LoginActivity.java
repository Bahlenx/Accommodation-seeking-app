package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onStart() {
        super.onStart();
        // The Session Gatekeeper (Auto-Login)
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            // User is already logged in, route them based on role or to a default dashboard.
            // Ideally, we'd fetch the role from Firestore, but for a quick route to the Inner app:
            Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 1. Back Button Logic
        ImageButton backButton = findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                // Clear the back stack and force navigation exactly to MainActivity (Greeting Page)
                Intent intent = new Intent(LoginActivity.this, OuterMainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        // 2. Firebase Login Logic
        EditText emailInput = findViewById(R.id.editTextText);
        EditText passwordInput = findViewById(R.id.editTextText2);
        Button loginBtn = findViewById(R.id.button2);

        if (loginBtn != null) {
            loginBtn.setOnClickListener(v -> {
                String email = emailInput.getText().toString().trim();
                String password = passwordInput.getText().toString().trim();

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Please enter both Email and Password", Toast.LENGTH_SHORT).show();
                    return;
                }

                loginBtn.setEnabled(false);
                Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show();

                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                                
                                // Fetch user role from Firestore to determine which dashboard to show
                                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                                FirebaseFirestore.getInstance()
                                        .collection("Users").document(userId)
                                        .get()
                                        .addOnSuccessListener(documentSnapshot -> {
                                            String role = documentSnapshot.getString("role");
                                            Intent homeIntent;
                                            
                                            // Route to Inner App activities based on the role
                                            if ("Landlord".equals(role)) {
                                                homeIntent = new Intent(LoginActivity.this, LandlordDashboardActivity.class);
                                            } else {
                                                // Default to Tenant Home Activity
                                                homeIntent = new Intent(LoginActivity.this, HomeActivity.class);
                                            }
                                            
                                            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                            startActivity(homeIntent);
                                            finish();
                                        })
                                        .addOnFailureListener(e -> {
                                            // Fallback if Firestore fails, send to Tenant Home
                                            Intent homeIntent = new Intent(LoginActivity.this, HomeActivity.class);
                                            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                            startActivity(homeIntent);
                                            finish();
                                        });
                            } else {
                                loginBtn.setEnabled(true);
                                String errorMessage = task.getException() != null ? task.getException().getMessage() : "Authentication failed";
                                Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
            });
        }
    }
}
