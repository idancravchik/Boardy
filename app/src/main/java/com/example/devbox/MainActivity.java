package com.example.devbox;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.vectordrawable.graphics.drawable.SeekableAnimatedVectorDrawable;

import com.example.devbox.db.Student;
import com.example.devbox.ui.DepthButton;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;


public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DepthButton btnLogout;
    private DepthButton btnNewProject;
    private DepthButton btnMyProjects;
    private TextView tvFullname;
    private TextView tvSchool;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);


        btnLogout = findViewById(R.id.btnLogout);
        btnNewProject = findViewById(R.id.btnNew);
        btnMyProjects = findViewById(R.id.btnMyProjects);
        tvFullname = findViewById(R.id.tvFullname);
        tvSchool = findViewById(R.id.tvSchool);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        //מייבאים את המשתמש ושמים את הפרטים שלו בממשק
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
            Student currentStudent = documentSnapshot.toObject(Student.class);
            tvFullname.setText(currentStudent.getFullName());
            tvSchool.setText(currentStudent.getSchool());
        });

        btnNewProject.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, EditorActivity.class));
        });
        btnMyProjects.setOnClickListener(view -> {
            startActivity(new Intent(MainActivity.this, PrivateProjectsActivity.class));
        });
        btnLogout.setOnClickListener(view -> logout());


    }

    @Override
    protected void onStart() {
        super.onStart();
        // בודק אם המשתמש שהגיע מחובר ואם לא משגר אותו למסך התחברות
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void logout() {
        mAuth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }


}
