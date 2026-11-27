package com.example.devbox;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
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

import com.example.devbox.ui.DepthButton;
import com.flashingservice.Ch55xHidFlasher;

import java.io.IOException;

public class UploaderActivity extends AppCompatActivity {

    //-------------------אלמנטים----------------------------
    private ImageView btnUpload;
    private SeekableAnimatedVectorDrawable btnUpload_hold;
    private SeekableAnimatedVectorDrawable upload_loaderAvd;
    private SeekableAnimatedVectorDrawable loaderAvd;
    private ImageView btnBack;
    private DepthButton btnHome;
    private TextView tvUpload;
    private TextView tvProjectName;
    private ViewFlipper vfUploader;


    //-------------------שירותים----------------------------
    private final ValueAnimator timer = new ValueAnimator();
    long maxDuration;
    private MediaPlayer seHold;
    private MediaPlayer seSuccess;

    private Ch55xHidFlasher flasher;

    //-------------------משתנים ותכונות---------------------
    enum UploaderState {
        CONNECTION_COMPILATION,
        UPLOAD_READY,
        UPLOAD_FINISHED
    }
    private String cCode; // הקוד מהגנרטור בC שמקבלים באינטנט
    private String projectName; // שם הפרויקט שמקבלים באינטנט
    private UsbDevice device;

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
        tvProjectName = findViewById(R.id.tvProjectName);
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);
        btnUpload = findViewById(R.id.btUpload);
        btnUpload_hold = SeekableAnimatedVectorDrawable.create(this, R.drawable.avd_upload_hold);
        btnUpload.setImageDrawable(btnUpload_hold);
        flasher = new Ch55xHidFlasher(this);

        maxDuration = btnUpload_hold.getTotalDuration(); // לוקחים את האורך האמיתי מה-AVD
        timer.setInterpolator(new LinearInterpolator());
        seHold = MediaPlayer.create(this, R.raw.se_button_charge);
        seSuccess = MediaPlayer.create(this, R.raw.se_upload_success);


        //----------------קוד תהליך----------------
        tvProjectName.setText(projectName);
        updateProcess(UploaderState.CONNECTION_COMPILATION);


        //----------------ליסנרים------------------
        btnBack.setOnClickListener(v -> { startActivity(new Intent(UploaderActivity.this, EditorActivity.class));}); // חזרה לעמוד קודם
        btnHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(UploaderActivity.this, MainActivity.class));
                finish();
            }
        });

        //אלו ליסנרים לניהול הכפתור העלאה:
        timer.addUpdateListener(animation -> {
            int animatedValue = (int) animation.getAnimatedValue();
            // הגנה מפני חריגה מגבולות 0 ועד maxDuration
            long playTime = Math.max(0L, Math.min(maxDuration, animatedValue));
            btnUpload_hold.setCurrentPlayTime(playTime);
        }); // שם את האנימציה במקום הנכון לפי הזמן

        timer.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                switchToLoader(animation);
            }
        });
        btnUpload.setOnTouchListener((view, motionEvent) -> {
            uploadTouchManager(view, motionEvent);
            return true;
        });


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

    //-----------------תפעול גרפיקה----------------------
    public void finishLoader(View view) throws IOException {
        upload_loaderAvd.stop();
        SeekableAnimatedVectorDrawable finishAvd = SeekableAnimatedVectorDrawable.create(UploaderActivity.this, R.drawable.avd_upload_finish);
        btnUpload.setImageDrawable(finishAvd);
        //עכשיו קולבק שעובר עמוד רק כשהאנימציה הסתיימה
        finishAvd.registerAnimationCallback(new SeekableAnimatedVectorDrawable.AnimationCallback() {
            @Override
            public void onAnimationEnd(@NonNull SeekableAnimatedVectorDrawable drawable) {
                super.onAnimationEnd(drawable);
                finishAvd.unregisterAnimationCallback(this); //מכבה את הקולבק
                updateProcess(UploaderState.UPLOAD_FINISHED);
            }
        });
        finishAvd.start();
        seSuccess.start();
    }
    //זה פעולה שמנהלת את התזמון של הלחיצה על הכפתור העלאה וסופרת זמן עד שזה מסתיים
    public boolean uploadTouchManager(View v, MotionEvent event) {
        // המיקום המדויק שבו האנימציה נמצאת כרגע (אם טרם התחילה, הערך הוא 0)
        int currentPos = timer.getAnimatedValue() != null ? (int) timer.getAnimatedValue() : 0;

        if (upload_loaderAvd != null) { // זה מוע שילחצון אחרי שכבר עבנו לשלב הבא של הלודר
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.d("BUTTON", "DOWN!");

                seHold.seekTo(0); // מפעיל מוזיקה
                seHold.start();

                timer.cancel(); // עוצרים כל תנועה קודמת
                timer.setIntValues(currentPos, (int) maxDuration); // רצים מהנקודה הנוכחית עד הסוף
                timer.setDuration(maxDuration - currentPos); // הזמן שנשאר עד הסוף
                timer.start();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                Log.d("BUTTON", "UP!");
                if (currentPos > 0 && currentPos < maxDuration) {

                    seHold.pause();

                    timer.cancel(); // עוצרים את ההתקדמות קדימה
                    timer.setIntValues(currentPos, 0); // רצים בדיוק מהנקודה הנוכחית חזרה ל0
                    timer.setDuration(300L); // חזרה מהירה
                    timer.start();
                }
                return true;
        }
        return false;
    }
    //זה פעולה שמעבירה בסוף הזמן לאנימציה של הלודר ועוצרת את הקודם
    public void switchToLoader(Animator animation) {
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
    //----------------------------------------------------


}