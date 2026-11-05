package com.example.devbox;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.devbox.ui.DepthButton;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);


        setContentView(R.layout.activity_login);


    }

    public void switchToRegister(View view) {
        setContentView(R.layout.activity_register);
    }

    public void switchToMain(View view) {
        setContentView(R.layout.activity_main);
    }
}
