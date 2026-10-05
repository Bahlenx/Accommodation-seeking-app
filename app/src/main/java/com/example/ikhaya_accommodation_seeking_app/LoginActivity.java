package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
// Firestore import removed as we are migrating to Supabase

public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onStart() {
        super.onStart();
        // The Session Gatekeeper (Auto-Login)
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // User is already logged in, fetch the fresh token and proceed to Supabase
            user.getIdToken(true).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String idToken = task.getResult().getToken();
                    String userId = user.getUid();
                    proceedToSupabaseBackend(idToken, userId);
                } else {
                    Toast.makeText(LoginActivity.this, "Failed to refresh authentication", Toast.LENGTH_SHORT).show();
                }
            });
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
                                
                                com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                                if (user != null) {
                                    // Pass 'true' to force refresh and ensure you get the latest custom claims
                                    user.getIdToken(true).addOnCompleteListener(tokenTask -> {
                                        if (tokenTask.isSuccessful()) {
                                            String idToken = tokenTask.getResult().getToken();
                                            String uid = user.getUid();
                                            
                                            // Log the idToken and the uid to the console so we can verify this part works
                                            android.util.Log.d("FirebaseLogin", "ID Token: " + idToken);
                                            android.util.Log.d("FirebaseLogin", "User UID: " + uid);
                                            
                                            // Proceed to fetch the application role from Supabase
                                            proceedToSupabaseBackend(idToken, uid);
                                        } else {
                                            loginBtn.setEnabled(true);
                                            Toast.makeText(LoginActivity.this, "Failed to retrieve authentication token", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            } else {
                                loginBtn.setEnabled(true);
                                String errorMessage = task.getException() != null ? task.getException().getMessage() : "Authentication failed";
                                Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
            });
        }
    }

    /**
     * Placeholder method for the teammate to implement the Supabase HTTP request.
     * Use the idToken as the Bearer token in the Authorization header.
     */
    private void proceedToSupabaseBackend(String idToken, String uid) {
        // TODO: Implement HTTP request to Supabase profiles endpoint using the idToken
        // Example logic for your teammate:
        // 1. Send GET request to https://<YOUR_PROJECT_REF>.supabase.co/rest/v1/profiles?user_id=eq.<uid>&select=user_role
        // 2. Add Headers:
        //    Authorization: Bearer <idToken>
        //    apikey: <your_supabase_anon_key>
        // 3. Parse the user_role from JSON
        // 4. Route to LandlordDashboardActivity, TenantDashboardActivity, etc. based on role
        
        android.util.Log.d("SupabaseIntegration", "proceedToSupabaseBackend called. Ready for HTTP implementation.");
        android.util.Log.d("SupabaseIntegration", "Received UID: " + uid);
        android.util.Log.d("SupabaseIntegration", "Received Token: " + idToken);
        
        // Temporarily routing to HomeActivity so the app still functions while the teammate works on this
        Intent homeIntent = new Intent(LoginActivity.this, HomeActivity.class);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(homeIntent);
        finish();
    }
}
