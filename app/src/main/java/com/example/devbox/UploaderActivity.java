package com.example.devbox;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.vectordrawable.graphics.drawable.SeekableAnimatedVectorDrawable;

public class UploaderActivity extends AppCompatActivity {

    private ImageView btnUpload;
    private SeekableAnimatedVectorDrawable btUpload_hold;
    private SeekableAnimatedVectorDrawable loaderAvd;
    private ImageView btnBack;
    private final ValueAnimator timer = new ValueAnimator();

    private MediaPlayer buttonCharge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uploader);

        btnBack = findViewById(R.id.btnBack);
        buttonCharge = MediaPlayer.create(this, R.raw.se_button_charge);

        btnUpload = findViewById(R.id.btUpload);
        btUpload_hold = SeekableAnimatedVectorDrawable.create(this, R.drawable.avd_upload_hold);
        btnUpload.setImageDrawable(btUpload_hold);
        long maxDuration = btUpload_hold.getTotalDuration(); // לוקחים את האורך האמיתי מה-AVD
        timer.setInterpolator(new LinearInterpolator());


        btnBack.setOnClickListener(v -> { // חזרה לעמוד הקודם
            startActivity(new Intent(UploaderActivity.this, EditorActivity.class));
            finish();
        });

        //אלו ליסנרים לניהול הכפתור:
        timer.addUpdateListener(animation -> {
            int animatedValue = (int) animation.getAnimatedValue();
            // הגנה מפני חריגה מגבולות 0 ועד maxDuration
            long playTime = Math.max(0L, Math.min(maxDuration, animatedValue));
            btUpload_hold.setCurrentPlayTime(playTime);
        }); // שם את האנימציה במקום הנכון לפי הזמן

        timer.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // בודקים אם הגענו ממש לסוף (ולא נעצרנו באמצע ע"י cancel)
                if (timer.getAnimatedValue() != null && (int) timer.getAnimatedValue() == maxDuration) {
                    Toast.makeText(UploaderActivity.this, "הטעינה הושלמה!", Toast.LENGTH_SHORT).show();
                    loaderAvd = (SeekableAnimatedVectorDrawable)SeekableAnimatedVectorDrawable.create(UploaderActivity.this, R.drawable.avd_upload_loader);
                    btnUpload.setImageDrawable(loaderAvd);
                    if (loaderAvd != null) {
                        btnUpload.setClickable(false);
                        loaderAvd.start();
                    }

                }
            }
        });
        btnUpload.setOnTouchListener((v, event) -> {
            // המיקום המדויק שבו האנימציה נמצאת כרגע (אם טרם התחילה, הערך הוא 0)
            int currentPos = timer.getAnimatedValue() != null ? (int) timer.getAnimatedValue() : 0;

            if (loaderAvd != null) { // זה מוע שילחצון אחרי שכבר עבנו לשלב הבא של הלודר
                return true;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.d("BUTTON", "DOWN!");

                    buttonCharge.seekTo(0); // מפעיל מוזיקה
                    buttonCharge.start();

                    timer.cancel(); // עוצרים כל תנועה קודמת
                    timer.setIntValues(currentPos, (int) maxDuration); // רצים מהנקודה הנוכחית עד הסוף
                    timer.setDuration(maxDuration - currentPos); // הזמן שנשאר עד הסוף
                    timer.start();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Log.d("BUTTON", "UP!");
                    if (currentPos > 0 && currentPos < maxDuration) {

                        buttonCharge.pause();

                        timer.cancel(); // עוצרים את ההתקדמות קדימה
                        timer.setIntValues(currentPos, 0); // רצים בדיוק מהנקודה הנוכחית חזרה ל0
                        timer.setDuration(300L); // חזרה מהירה
                        timer.start();
                    }
                    return true;
            }
            return false;
        }); // מנהל את הלחיצה על הכפתור ואת החזרה אחורה


    }

    public void finishLoader(View view) {
        loaderAvd.stop();
        SeekableAnimatedVectorDrawable finishAvd = SeekableAnimatedVectorDrawable.create(UploaderActivity.this, R.drawable.avd_upload_finish);
        btnUpload.setImageDrawable(finishAvd);
        finishAvd.start();
    }


}