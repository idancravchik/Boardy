package com.example.devbox;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.devbox.db.Project;
import com.example.devbox.db.Student;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.sql.Ref;

public class EditorActivity extends AppCompatActivity {

    MediaPlayer mp;
    private FirebaseFirestore db;
    private FirebaseAuth mauth;
    private WebView webView;
    private BottomSheetDialog dSettings;
    private Button btnSaveSettings;
    private ImageView btnSettings;
    private ImageView btnToUpload;
    private ImageView btnSaveProject;
    private ImageView btnBack;
    private TextView tvProjectName;
    private TextInputLayout etProjectName;
    private TextInputLayout etDescription;
    private TextInputLayout dmSensor1;
    private TextInputLayout dmSensor2;
    private MaterialSwitch swPublic;
    private String intentfrom;
    private Project currentProject;
    private Student currentStudent;
    private String cCode; // הקוד המוכן מהגנרטור

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        mauth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        mp = MediaPlayer.create(this, R.raw.se_upload_success);

        //הגדרת הרשאות והגדרות הוובויו
        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");

        dSettings = new BottomSheetDialog(this);
        dSettings.setContentView(R.layout.dialog_project_settings);
        dSettings.setCancelable(false);
        btnSaveSettings = dSettings.findViewById(R.id.btnSaveProject);
        etProjectName = dSettings.findViewById(R.id.etProjectName);
        etDescription = dSettings.findViewById(R.id.etDescription);
        dmSensor1 = dSettings.findViewById(R.id.dmSensor1);
        dmSensor2 = dSettings.findViewById(R.id.dmSensor2);
        swPublic = dSettings.findViewById(R.id.swPublic);
        btnSettings = findViewById(R.id.btnSettings);
        btnToUpload = findViewById(R.id.btnToUpload);
        btnSaveProject = findViewById(R.id.btnSaveProject);
        btnBack = findViewById(R.id.btnBack);
        tvProjectName = findViewById(R.id.tvProjectName);

        //טוען משתמש נוכחי
        db.collection("users").document(mauth.getCurrentUser().getUid()).get().addOnSuccessListener(documentSnapshot -> currentStudent = documentSnapshot.toObject(Student.class));

        dSettings.setOnDismissListener(dialog -> {
            // אם המשתמש נכנס בטעות ויוצא חוזרים למסך הבית
            if (currentProject == null) {
                finish();
            }
        });

        //בדיקה מאיפה הגיעו והפניה או להגדרות חדשות או לעריכה
        intentfrom = getIntent().getStringExtra("projectId");
        if (intentfrom == null) {
            dSettings.setCancelable(true);
            dSettings.show();
        } else {
            //מייבא את הפרויקט וקורא לטעינה שלו
            db.collection("projects").document(intentfrom).get().addOnSuccessListener(documentSnapshot -> {
                currentProject = documentSnapshot.toObject(Project.class);
                loadProject();
            });

        }
        //ניתוב כפתור שמירת הגדרות לפרויקט חדש או קיים
        btnSaveSettings.setOnClickListener(v -> {
            if (intentfrom == null) {
                newProject();
            } else {
                updateProjectSettings();
            }
        });
        btnSettings.setOnClickListener(v -> loadProjectSettings());
        btnToUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //מעביר לתהליך של ההעלאה עם כל המידע שצריך
                String projectName = currentProject != null ? currentProject.getTitle() : "";
                webView.evaluateJavascript("cGenerator.workspaceToCode(ws);", value -> {
                    cCode = value;
                    Log.d("EDITOR", cCode != null ? cCode : "");
                    Intent intent = new Intent(EditorActivity.this, UploaderActivity.class);
                    intent.putExtra("projectName", projectName);
                    intent.putExtra("cCode", cCode);
                    startActivity(intent);
                });
            }
        });
        btnSaveProject.setOnClickListener(v -> saveProject(false));
        btnBack.setOnClickListener(v -> saveProject(true));
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveProject(true);
            }
        });
    }



    //נקרא כשפותחים את החלון מפרויקט קיים ואחראי לטעון את הפרויקט לסביבת העבודה
    private void loadProject() {
        tvProjectName.setText(currentProject.getTitle());
        webView.evaluateJavascript("Blockly.serialization.workspaces.load(" + currentProject.getBlocksData() + ", ws);", null);
    }
    //נקרא כשלוחצים שמור בהגדרות פרויקט חדש, ואחראי לפתוח אותו כפרויקט חדש בפיירסטור
    private void newProject() {
        btnSaveSettings.setEnabled(false);

        //בדיקה שיש לפרויקט שם
        if (etProjectName.getEditText().getText().toString().isEmpty()) {
            Toast.makeText(this, "חייב להביא שם לפרויקט", Toast.LENGTH_SHORT).show();
            btnSaveSettings.setEnabled(true);
            return;
        }

        //הכנת פרמטרים לפרויקט החדש
        DocumentReference ref = db.collection("projects").document();
        String newProjectId = ref.getId();
        String port1Sensor;
        String port2Sensor;
        switch (dmSensor1.getEditText().getText().toString()) {
            case "חיישן מזג אוויר":
                port1Sensor = "temphum";
                break;
            case "מנורה":
                port1Sensor = "led";
                break;
            case "מנוע":
                port1Sensor = "servo";
                break;
            case "לחצן":
                port1Sensor = "button";
                break;
            default:
                port1Sensor = "";
        }
        switch (dmSensor2.getEditText().getText().toString()) {
            case "חיישן מזג אוויר":
                port2Sensor = "temphum";
                break;
            case "מנורה":
                port2Sensor = "led";
                break;
            case "מנוע":
                port2Sensor = "servo";
                break;
            case "לחצן":
                port2Sensor = "button";
                break;
            default:
                port2Sensor = "";
        }

        //יצורים את הפרויקט החדש, מעלים לפיירסטור, וטוענים למסך
        Project newProject = new Project(
                newProjectId,
                etProjectName.getEditText().getText().toString(),
                etDescription.getEditText().getText().toString(),
                "",
                port1Sensor,
                port2Sensor,
                swPublic.isChecked(),
                currentStudent.getId(),
                currentStudent.getFullName(),
                currentStudent.getSchool(),
                System.currentTimeMillis(),
                System.currentTimeMillis());
        ref.set(newProject).addOnSuccessListener(aVoid -> {
            currentProject = newProject;
            btnSaveSettings.setEnabled(true);
            dSettings.setCancelable(true);
            Toast.makeText(this, "הפרויקט שלך נוצר בהצלחה!", Toast.LENGTH_SHORT).show();
            dSettings.dismiss();
            loadProject();
            //טוענים בלוק בסיס
            webView.evaluateJavascript("(() => { const b = ws.newBlock('start'); b.setDeletable(false); b.initSvg(); b.render(); b.moveBy(150, 50); })();", null);
        }).addOnFailureListener(e -> {
            btnSaveSettings.setEnabled(true);
            Toast.makeText(this, "שגיאה ביצירת הפרויקט", Toast.LENGTH_SHORT).show();
        });
    }
    //נקרא כשלוחצים שמור בהגדרות פרויקט קיים, ואחראי לעדכן את האובייקט בפיירסטור
    private void updateProjectSettings() {
        btnSaveSettings.setEnabled(false);

        //בדיקה שיש לפרויקט שם
        if (etProjectName.getEditText().getText().toString().isEmpty()) {
            Toast.makeText(this, "חייב להביא שם לפרויקט", Toast.LENGTH_SHORT).show();
            btnSaveSettings.setEnabled(true);
            return;
        }

        //הכנת פרמטרים לפרויקט החדש
        String port1Sensor;
        String port2Sensor;
        switch (dmSensor1.getEditText().getText().toString()) {
            case "חיישן מזג אוויר":
                port1Sensor = "temphum";
                break;
            case "מנורה":
                port1Sensor = "led";
                break;
            case "מנוע":
                port1Sensor = "servo";
                break;
            case "לחצן":
                port1Sensor = "button";
                break;
            default:
                port1Sensor = "";
        }
        switch (dmSensor2.getEditText().getText().toString()) {
            case "חיישן מזג אוויר":
                port2Sensor = "temphum";
                break;
            case "מנורה":
                port2Sensor = "led";
                break;
            case "מנוע":
                port2Sensor = "servo";
                break;
            case "לחצן":
                port2Sensor = "button";
                break;
            default:
                port2Sensor = "";
        }

        //יצורים את הפרויקט החדש, מעלים לפיירסטור, וטוענים למסך
        currentProject.setTitle(etProjectName.getEditText().getText().toString());
        currentProject.setDescription(etDescription.getEditText().getText().toString());
        currentProject.setPublic(swPublic.isChecked());
        currentProject.setPort1Sensor(port1Sensor);
        currentProject.setPort2Sensor(port2Sensor);
        currentProject.setUpdatedAt(System.currentTimeMillis());

        db.collection("projects").document(currentProject.getId()).set(currentProject).addOnSuccessListener(aVoid -> {
            btnSaveSettings.setEnabled(true);
            dSettings.setCancelable(true);
            Toast.makeText(this, "הפרויקט שלך נשמר בהצלחה!", Toast.LENGTH_SHORT).show();
            dSettings.dismiss();
            loadProject();
        }).addOnFailureListener(e -> {
            btnSaveSettings.setEnabled(true);
            Toast.makeText(this, "שגיאה בשמירת הפרויקט", Toast.LENGTH_SHORT).show();
        });
    }
    //נקרא כשפותחים את התפריט הגדרות בפרויקט קיים ואחראי לטעון את המידע הקיים לתוך השדות
    private void loadProjectSettings() {
    //מילוי שדות
    etProjectName.getEditText().setText(currentProject.getTitle());
    etDescription.getEditText().setText(currentProject.getDescription());
    swPublic.setChecked(currentProject.isPublic());

    String sensor1Display;
    switch (currentProject.getPort1Sensor() != null ? currentProject.getPort1Sensor() : "") {
        case "temphum":
            sensor1Display = "חיישן מזג אוויר";
            break;
        case "led":
            sensor1Display = "מנורה";
            break;
        case "servo":
            sensor1Display = "מנוע";
            break;
        case "button":
            sensor1Display = "לחצן";
            break;
        default:
            sensor1Display = "";
    }
    String sensor2Display;
    switch (currentProject.getPort2Sensor() != null ? currentProject.getPort2Sensor() : "") {
        case "temphum":
            sensor2Display = "חיישן מזג אוויר";
            break;
        case "led":
            sensor2Display = "מנורה";
            break;
        case "servo":
            sensor2Display = "מנוע";
            break;
        case "button":
            sensor2Display = "לחצן";
            break;
        default:
            sensor2Display = "";
    }
    AutoCompleteTextView acSensor1 = (AutoCompleteTextView) dmSensor1.getEditText();
    AutoCompleteTextView acSensor2 = (AutoCompleteTextView) dmSensor2.getEditText();
    acSensor1.setText(sensor1Display, false);
    acSensor2.setText(sensor2Display, false);

    dSettings.setCancelable(true);
    dSettings.show();


}
    private void saveProject(boolean exitAfterSave) {
        webView.evaluateJavascript("Blockly.serialization.workspaces.save(ws);", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                //מעדכן את הפיירבייס עם הבלוקים החדשים
                currentProject.setBlocksData(value);
                db.collection("projects").document(currentProject.getId()).set(currentProject).addOnSuccessListener(unused -> {
                    Toast.makeText(EditorActivity.this, "הפרויקט נשמר בהצלחה!", Toast.LENGTH_SHORT).show();
                    mp.start();
                    if (exitAfterSave) {
                        finish();
                    }
                });

            }
        });
    }
}