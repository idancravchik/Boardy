package com.example.devbox;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.vectordrawable.graphics.drawable.SeekableAnimatedVectorDrawable;

import java.io.IOException;

public class UploaderActivity extends AppCompatActivity {

    //-------------------אלמנטים----------------------------
    private ImageView btnUpload;
    private SeekableAnimatedVectorDrawable btUpload_hold;
    private SeekableAnimatedVectorDrawable upload_loaderAvd;
    private SeekableAnimatedVectorDrawable loaderAvd;
    private ImageView btnBack;
    private TextView tvUpload;
    private ViewFlipper vfUploader;


    //-------------------שירותים----------------------------
    private final ValueAnimator timer = new ValueAnimator();
    private MediaPlayer mp;

    //-------------------משתנים ותכונות---------------------
    enum UploaderState {
        CONNECTION_COMPILATION,
        UPLOAD_READY,
        UPLOAD_FINISHED
    }
    private String cCode; // הקוד מהגנרטור בC שמקבלים באינטנט
    private String projectName; // שם הפרויקט שמקבלים באינטנט

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uploader);

        //--------------הגדרות ואיפוסים-------------------------
        Intent intent = getIntent(); // קבלת המידע להעלאה מהעורך קוד
        cCode = intent.getStringExtra("cCode");
        projectName = intent.getStringExtra("projectName");

        vfUploader = findViewById(R.id.vfUploader);
        tvUpload = findViewById(R.id.tvUpload);
        btnBack = findViewById(R.id.btnBack);
        btnUpload = findViewById(R.id.btUpload);
        btUpload_hold = SeekableAnimatedVectorDrawable.create(this, R.drawable.avd_upload_hold);
        btnUpload.setImageDrawable(btUpload_hold);

        long maxDuration = btUpload_hold.getTotalDuration(); // לוקחים את האורך האמיתי מה-AVD
        timer.setInterpolator(new LinearInterpolator());
        mp = MediaPlayer.create(this, R.raw.se_button_charge);
        //----------------קוד התחלה----------------
        updateProcess(UploaderState.UPLOAD_READY);

        //----------------ליסנרים------------------
        btnBack.setOnClickListener(v -> { // חזרה לעמוד הקודם
            startActivity(new Intent(UploaderActivity.this, EditorActivity.class));
            finish();
        });

        //אלו ליסנרים לניהול הכפתור העלאה:
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
                    upload_loaderAvd = (SeekableAnimatedVectorDrawable)SeekableAnimatedVectorDrawable.create(UploaderActivity.this, R.drawable.avd_upload_loader);
                    btnUpload.setImageDrawable(upload_loaderAvd);
                    if (upload_loaderAvd != null) {
                        btnUpload.setClickable(false);
                        upload_loaderAvd.start();
                        tvUpload.setText("מעלה...");
                    }

                }
            }
        });
        btnUpload.setOnTouchListener((v, event) -> {
            // המיקום המדויק שבו האנימציה נמצאת כרגע (אם טרם התחילה, הערך הוא 0)
            int currentPos = timer.getAnimatedValue() != null ? (int) timer.getAnimatedValue() : 0;

            if (upload_loaderAvd != null) { // זה מוע שילחצון אחרי שכבר עבנו לשלב הבא של הלודר
                return true;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.d("BUTTON", "DOWN!");

                    mp.seekTo(0); // מפעיל מוזיקה
                    mp.start();

                    timer.cancel(); // עוצרים כל תנועה קודמת
                    timer.setIntValues(currentPos, (int) maxDuration); // רצים מהנקודה הנוכחית עד הסוף
                    timer.setDuration(maxDuration - currentPos); // הזמן שנשאר עד הסוף
                    timer.start();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Log.d("BUTTON", "UP!");
                    if (currentPos > 0 && currentPos < maxDuration) {

                        mp.pause();

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

    public void updateProcess(@NonNull UploaderState state) {
        switch (state) {
            case CONNECTION_COMPILATION:
                vfUploader.setDisplayedChild(0);
                loaderAvd = (SeekableAnimatedVectorDrawable)SeekableAnimatedVectorDrawable.create(UploaderActivity.this, R.drawable.avd_upload_loader);
                loaderAvd.start();
                break;
            case UPLOAD_READY:
                vfUploader.setDisplayedChild(1);
                break;
            case UPLOAD_FINISHED:
                vfUploader.setDisplayedChild(2);
                break;
        }
    }

    public void finishLoader(View view) throws IOException {
        upload_loaderAvd.stop();
        SeekableAnimatedVectorDrawable finishAvd = SeekableAnimatedVectorDrawable.create(UploaderActivity.this, R.drawable.avd_upload_finish);
        btnUpload.setImageDrawable(finishAvd);
        finishAvd.start();
        updateProcess(UploaderState.UPLOAD_FINISHED);
    }


}