package com.mysql.pocketsql;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize SQL API helper and trigger default DB checks/loading early
        com.mysql.pocketsql.engine.SqlApiHelper.init(this);
        



        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // Trigger Google Play Integrity check on app startup
        com.mysql.pocketsql.engine.AppIntegrityManager.checkAppIntegrity(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_container, new SplashFragment())
                    .commit();
        }
    }
}
