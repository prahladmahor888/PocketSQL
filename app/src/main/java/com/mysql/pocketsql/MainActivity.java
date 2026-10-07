package com.mysql.pocketsql;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_container, new SplashFragment())
                    .commit();
        }

        // Initialize SQL Engine and API Helper asynchronously using CPU Thread Scheduler
        com.mysql.pocketsql.engine.SqlThreadScheduler.runDatabaseInitTask(() -> {
            com.mysql.pocketsql.engine.SqlApiHelper.init(MainActivity.this);
            com.mysql.pocketsql.engine.AppIntegrityManager.checkAppIntegrity(MainActivity.this);
        });

        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            new SettingsManager(this).applyFontToViewTree(mainView);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    /**
     * Dynamically updates the status bar, navigation bar, and root container background
     * to match the active theme color seamlessly.
     */
    public void updateSystemBarsAndTheme(int color) {
        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            mainView.setBackgroundColor(color);
        }

        Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(color);
            window.setNavigationBarColor(color);

            WindowInsetsControllerCompat controller = 
                WindowCompat.getInsetsController(window, window.getDecorView());
            if (controller != null) {
                // Calculate luminance: if color is light, use dark icons; otherwise use light icons
                double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
                boolean isLight = luminance > 0.6;
                controller.setAppearanceLightStatusBars(isLight);
                controller.setAppearanceLightNavigationBars(isLight);
            }
        }
    }
}
