package com.example.devbox;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.devbox.ui.DepthButton;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Firebase;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "FIREBASE";
    private FirebaseAuth mAuth;
    private DepthButton btnLogin;
    private TextInputLayout etEmail;
    private TextInputLayout etPassword;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        //הגדרת תכונות ומשתנים
        btnLogin = findViewById(R.id.btnLogin);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        mAuth = FirebaseAuth.getInstance();

        //ליסנרים
        btnLogin.setOnClickListener(view -> login());
    }

    private void login() {
        String email = etEmail.getEditText().getText().toString();
        String password = etPassword.getEditText().getText().toString();
        Log.d(TAG, email);
        Log.d(TAG, password);


        //בודק שהשדות לא ריקים
        if(TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "חייבים להזין שם משתמש וסיסמא", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // אם ההתחברות הצליחה, שולח ללוג וממשיך למסך הראשי
                            Log.d(TAG, "signInWithEmail:success");
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            // אם ההתחברות לא הצליחה, שולח לוג והודעה למשתמש
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "ההתחברות כשלה", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        Log.d(TAG, "after");
    }
}