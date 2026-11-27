package com.example.devbox;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.devbox.db.Project;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;

public class PrivateProjectsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mauth;
    private RecyclerView rvProjects;
    private ProjectsAdapter adapter;
    private ArrayList<Project> projects = new ArrayList<Project>();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_private_projects);

        rvProjects = findViewById(R.id.rvProjects);
        rvProjects.setLayoutManager(new LinearLayoutManager(this));
        mauth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();


        //ייבוא הפרויקטים של המשתמש לרשימה והצגה שלה

        adapter = new ProjectsAdapter(projects);
        rvProjects.setAdapter(adapter);
        Log.d("IMPORTANT", "before update list");
        db.collection("projects").whereEqualTo("userId", mauth.getCurrentUser().getUid()).get().addOnSuccessListener(queryDocumentSnapshots -> {
            projects.addAll(queryDocumentSnapshots.toObjects(Project.class));
            adapter.updateList(projects);
        });




    }

    private class ProjectsAdapter extends RecyclerView.Adapter<ProjectsAdapter.ProjectViewHolder> {

        private ArrayList<Project> projectsList;

        public ProjectsAdapter(ArrayList<Project> projectsList) {
            this.projectsList = projectsList;
        }

        @NonNull
        @Override
        public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_private_project, parent, false);
            return new ProjectViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
            Project project = projectsList.get(position);

            // שם ותיאור הפרויקט
            holder.tvProjectName.setText(project.getTitle());
            holder.tvProjectDescription.setText(project.getDescription());

            // צ'יפ ציבורי / פרטי
            if (project.isPublic()) {
                holder.imgtypeChip.setImageResource(R.drawable.elm_public_chip);
            } else {
                holder.imgtypeChip.setImageResource(R.drawable.elm_private_chip);
            }

            // הגדרת תמונות החיישנים לפי פורט 1 ופורט 2
            setSensorImage(holder.imgSensor1, project.getPort1Sensor(), 1);
            setSensorImage(holder.imgSensor2, project.getPort2Sensor(), 2);

            // ==========================================================
            // כאן מתבצעת הפעולה כשלוחצים על כרטיס פרויקט
            // ==========================================================
            holder.itemView.setOnClickListener(v -> {
                String clickedProjectId = project.getId();
                // יש לך כאן גישה ישירה ל-PrivateProjectsActivity.this ולפונקציות של ה-Activity
                // TODO: הוסף כאן את הפעולה שלך עם clickedProjectId
                Intent intent = new Intent(PrivateProjectsActivity.this, EditorActivity.class);
                intent.putExtra("projectId", clickedProjectId);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return projectsList != null ? projectsList.size() : 0;
        }

        // פונקציית עזר לעדכון הרשימה (לחיפוש, סינון, הוספה או מחיקה)
        public void updateList(ArrayList<Project> newProjectsList) {
            this.projectsList = newProjectsList;
            notifyDataSetChanged();
        }

        // פונקציית עזר להתאמת תמונת החיישן או הסתרתה אם הערך null
        private void setSensorImage(ImageView imageView, String sensor, int port) {
            if (sensor == null || sensor.trim().isEmpty()) {
                imageView.setVisibility(View.GONE);
                return;
            }

            int drawableRes = 0;
            switch (sensor.toLowerCase().trim()) {
                case "led":
                    drawableRes = (port == 1) ? R.drawable.img_led_1 : R.drawable.img_led_2;
                    break;
                case "temphum":
                    drawableRes = (port == 1) ? R.drawable.img_temphum_1 : R.drawable.img_temphum_2;
                    break;
                case "button":
                    drawableRes = (port == 1) ? R.drawable.img_button_1 : R.drawable.img_button_2;
                    break;
                case "servo":
                    drawableRes = (port == 1) ? R.drawable.img_servo_1 : R.drawable.img_servo_2;
                    break;
            }

            if (drawableRes != 0) {
                imageView.setImageResource(drawableRes);
                imageView.setVisibility(View.VISIBLE);
            } else {
                imageView.setVisibility(View.GONE);
            }
        }

        class ProjectViewHolder extends RecyclerView.ViewHolder {
            TextView tvProjectName;
            TextView tvProjectDescription;
            ImageView imgtypeChip;
            ImageView imgSensor1;
            ImageView imgSensor2;

            public ProjectViewHolder(@NonNull View itemView) {
                super(itemView);
                tvProjectName = itemView.findViewById(R.id.tvProjectName);
                tvProjectDescription = itemView.findViewById(R.id.tvProjectDescription);
                imgtypeChip = itemView.findViewById(R.id.imgtypeChip);
                imgSensor1 = itemView.findViewById(R.id.imgSensor1);
                imgSensor2 = itemView.findViewById(R.id.imgSensor2);
            }
        }
    }
}
