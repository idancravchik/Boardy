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
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "FIREBASE";
    private FirebaseAuth mAuth;
    private DepthButton btnSignup;
    private TextInputLayout etEmail;
    private TextInputLayout etPassword;
    private TextInputLayout etPasswordVal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        btnSignup = findViewById(R.id.btnSignup);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etPasswordVal = findViewById(R.id.etPasswordVal);
        mAuth = FirebaseAuth.getInstance();

        btnSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signup();
            }
        });



    }

    private void signup() {
            String email = etEmail.getEditText().getText().toString();
            String password = etPassword.getEditText().getText().toString();
            String passwordVal = etPasswordVal.getEditText().getText().toString();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "חייבים לבחור שם משתמש וסיסמא", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(passwordVal)) {
                Toast.makeText(this, "אימות הסיסמא שונה מהסיסמא", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                // אם ההרשמה הצליחה, תעביר לעמוד הראשי.
                                Log.d(TAG, "createUserWithEmail:success");
                                startActivity(new Intent(SignupActivity.this, MainActivity.class));
                                finish();
                            } else {
                                // אם ההרשמה נכשלה, תשלח הודעה
                                Log.w(TAG, "createUserWithEmail:failure", task.getException());
                                Toast.makeText(SignupActivity.this, "ההרשמה נכשלה", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });


    }


}