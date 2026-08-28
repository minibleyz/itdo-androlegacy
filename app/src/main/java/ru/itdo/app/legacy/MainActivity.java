package ru.itdo.app.legacy;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends AppCompatActivity {

    // Начиная с этой версии Android рекомендуем современный клиент itdo-android
    // (Compose, minSdk 23) вместо этой legacy-сборки для старых устройств.
    private static final int RECOMMEND_MODERN_APP_FROM_SDK = Build.VERSION_CODES.M; // API 23, Android 6.0

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                Snackbar.make(view, "itdo legacy client", Snackbar.LENGTH_SHORT).show();
            }
        });

        if (Build.VERSION.SDK_INT >= RECOMMEND_MODERN_APP_FROM_SDK) {
            showModernAppRecommendation();
        }
    }

    private void showModernAppRecommendation() {
        new AlertDialog.Builder(this)
                .setTitle("Устаревшая версия приложения")
                .setMessage("Это приложение создано для старых версий Android. " +
                        "На вашем устройстве доступна современная версия itdo с более " +
                        "актуальным дизайном и функциями. Рекомендуем установить её.")
                .setCancelable(true)
                .setPositiveButton("Понятно", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }
}
