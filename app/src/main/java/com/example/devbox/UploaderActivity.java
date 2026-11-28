package com.example.devbox;

import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.hardware.usb.UsbDevice;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.vectordrawable.graphics.drawable.SeekableAnimatedVectorDrawable;

import com.example.devbox.ui.DepthButton;
import com.example.devbox.ui.UploadHoldButton;
import com.flashingservice.Ch55xHidFlasher;
import com.flashingservice.FlasherCallback;

public class UploaderActivity extends AppCompatActivity {

    //-------------------אלמנטים----------------------------
    private UploadHoldButton btnUpload;
    private ImageView loaderAvd;
    private ImageView btnBack;
    private DepthButton btnHome;
    private TextView tvUpload;
    private TextView tvProjectName;
    private TextView tvLoaderText;
    private TextView tvUploadCompleted;
    private ViewFlipper vfUploader;
    //-------------------שירותים----------------------------
    private Ch55xHidFlasher flasher;
    private MediaPlayer mp;
    //-------------------משתנים ותכונות---------------------
    enum UploaderState {
        CONNECTION_COMPILATION,
        UPLOAD_READY,
        UPLOAD_FINISHED
    }
    private String cCode; // הקוד מהגנרטור בC שמקבלים באינטנט
    private String hexCode; // הקוד אחרי הקומפילציה
    private String projectName; // שם הפרויקט שמקבלים באינטנט
    private UsbDevice device;
    private String testing = "";
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
        tvLoaderText = findViewById(R.id.tvLoaderText);
        tvUploadCompleted = findViewById(R.id.tvUploadCompleted);
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);
        btnUpload = findViewById(R.id.btUpload);
        loaderAvd = findViewById(R.id.imgloader);

        flasher = new Ch55xHidFlasher(this);
        mp = MediaPlayer.create(this, R.raw.se_upload_success);

        //----------------קוד תהליך----------------
        tvProjectName.setText(projectName); //מעצב את הלוח
        updateProcess(UploaderState.CONNECTION_COMPILATION);


        //----------------ליסנרים------------------
        //קולבק של המתנה לחיבור הלוח ומעבר לקומפילציה בסוף!
        flasher.startWatchingBoard(new Ch55xHidFlasher.BoardConnectionListener() {
            @Override
            public void onBoardReady(UsbDevice hidDevice) {
                device = hidDevice; //שומר את ההלוח שמחובר
                mp.start();
                tvLoaderText.setText("מכין קובץ להעלאה...");
                tvUploadCompleted.setVisibility(View.VISIBLE); //מראה את הטקסט של החיבור הושלם
                compileCode(); //שולח את הקוד לקומפילציה
            }

            @Override
            public void onBoardDisconnected() {
                Toast.makeText(UploaderActivity.this, "הלוח נותק", Toast.LENGTH_SHORT).show();
                device = null;
                updateProcess(UploaderState.CONNECTION_COMPILATION);
            }

            @Override
            public void onPermissionDenied() {
                Toast.makeText(UploaderActivity.this, "נדרשת הרשאת USB כדי להתחבר ללוח", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Throwable error) {
                Log.d("UPLOADER", "USB connection error", error);
                Toast.makeText(UploaderActivity.this, "שגיאה בחיבור ללוח", Toast.LENGTH_SHORT).show();
            }
        });
        // חזרה לעמוד קודם
        btnBack.setOnClickListener(v -> { startActivity(new Intent(UploaderActivity.this, EditorActivity.class));});
        //חזרה למסך הבית
        btnHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(UploaderActivity.this, MainActivity.class));
                finish();
            }
        });
        //העלאת הקוד בסוף הכפתור
        btnUpload.setOnUploadTriggerListener(() -> {
            tvUpload.setText("מעלה...");
            flashBoard(); //העלאת הקוד!!!
        });
        //-----------------------------------------

    }



    public void updateProcess(@NonNull UploaderState state) {
        switch (state) {
            case CONNECTION_COMPILATION:
                vfUploader.setDisplayedChild(0);
                ((Animatable)loaderAvd.getDrawable()).start(); //ניגש שתמונה שבפנים כאנימציה (המרה למטה) ומתחיל אותה
                break;
            case UPLOAD_READY:
                ((Animatable)loaderAvd.getDrawable()).stop();
                vfUploader.setDisplayedChild(1);
                break;
            case UPLOAD_FINISHED:
                vfUploader.setDisplayedChild(2);
                break;
        }
    }

    private void flashBoard() {
        flasher.flash(device, hexCode, new FlasherCallback() {
            @Override
            public void onProgress(int percent, int currentBytes, int totalBytes) {

            }

            @Override
            public void onStatus(String message) {
                Log.d("UPLOADER", message);
            }

            @Override
            public void onSuccess() {
                btnUpload.playFinishAnimation(() -> updateProcess(UploaderState.UPLOAD_FINISHED));
                if (flasher != null) {
                    flasher.stopWatchingBoard();
                }
            }

            @Override
            public void onError(Throwable error) {
                Log.e("UPLOADER", "Flash error", error);
                Toast.makeText(UploaderActivity.this, "שגיאה בצריבה: " + error.getMessage(), Toast.LENGTH_LONG).show();
                btnUpload.reset();
                tvUpload.setText("לחץ כדי להעלות");
                updateProcess(UploaderState.CONNECTION_COMPILATION);
            }
        });
    }

    private void compileCode() {
        //לשלוח בקשת בריאות לAPI לראות אם הוא עובד.
        //אם לא לשלוח טוסט שלא עובד ולחזור להתחלה
        //אם כן לשלוח את ה cCode לשם ולקבל את המידע לתוך hexCode
        //לעשות סאונדים ועיצובים של סיום ולעבור להעלאה
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (flasher != null) {
            flasher.stopWatchingBoard();
        }
    }

}
