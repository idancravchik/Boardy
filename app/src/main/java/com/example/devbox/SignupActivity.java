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

import com.example.devbox.db.Student;
import com.example.devbox.ui.DepthButton;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "FIREBASE";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DepthButton btnSignup;
    private TextInputLayout etEmail;
    private TextInputLayout etPassword;
    private TextInputLayout etPasswordVal;
    private TextInputLayout etFullName;
    private TextInputLayout etSchool;
    private TextInputLayout etBirthDate;
    private long birthDate;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        btnSignup = findViewById(R.id.btnSignup);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etPasswordVal = findViewById(R.id.etPasswordVal);
        etFullName = findViewById(R.id.etFullname);
        etSchool = findViewById(R.id.etSchool);
        etBirthDate = findViewById(R.id.datePickerLayout);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();


        btnSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signup();
            }
        });

        View.OnClickListener openDatePicker = v -> {
            CalendarConstraints constraints = new CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointBackward.now())
                    .build();

            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("בחר תאריך לידה")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .setCalendarConstraints(constraints)
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                if (etBirthDate.getEditText() != null) {
                    birthDate = selection;
                    etBirthDate.getEditText().setText(formatter.format(new Date(selection)));
                }
            });

            datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
        }; //ניהול הדייטפיקר של התאריך לידה
        etBirthDate.setEndIconOnClickListener(openDatePicker);
        if (etBirthDate.getEditText() != null) {
            etBirthDate.getEditText().setOnClickListener(openDatePicker);
        }
    }

    private void signup() {
            String email = etEmail.getEditText().getText().toString();
            String password = etPassword.getEditText().getText().toString();
            String passwordVal = etPasswordVal.getEditText().getText().toString();
            String fullName = etFullName.getEditText().getText().toString();
            String school = etSchool.getEditText().getText().toString();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "חייבים לבחור שם משתמש וסיסמא", Toast.LENGTH_SHORT).show();
                return;
            }
            if (birthDate == 0 || school.equals("") || fullName.equals("")) {
                Toast.makeText(this, "יש למלא את כל השדות", Toast.LENGTH_SHORT).show();
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

                                Log.d(TAG, "auth:createUserWithEmail:success");

                                //הוספת הפרטים לאחסון בפיירסטור
                                String uid = mAuth.getCurrentUser().getUid();
                                Student newStudent = new Student(uid, email, fullName, birthDate, school);
                                db.collection("users").document(uid).set(newStudent).addOnCompleteListener(task1 -> {
                                    if (task1.isSuccessful()) {
                                        Log.d(TAG, "firestore:createUserWithEmail:success");
                                        Toast.makeText(SignupActivity.this, "נרשמת בהצלחה!", Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(SignupActivity.this, MainActivity.class));
                                        finish();
                                    } else {
                                        Log.w(TAG, "firestore:createUserWithEmail:failure", task1.getException());
                                        Toast.makeText(SignupActivity.this, "ההרשמה נכשלה", Toast.LENGTH_SHORT).show();
                                    }
                                });


                            } else {
                                // אם ההרשמה נכשלה, תשלח הודעה
                                Log.w(TAG, "auth:createUserWithEmail:failure", task.getException());
                                Toast.makeText(SignupActivity.this, "ההרשמה נכשלה", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });


    }


}