package com.mysql.pocketsql;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mysql.pocketsql.engine.SqlApiHelper;
import com.mysql.pocketsql.engine.SqlThreadScheduler;

import java.util.ArrayList;
import java.util.List;

public class SetupFragment extends Fragment implements SqlApiHelper.DatabaseSetupListener {

    private CheckBox cbEcommerce;
    private CheckBox cbBanking;
    private CheckBox cbSchool;
    private CheckBox cbSocial;

    private LinearLayout layoutProgressSection;
    private TextView tvSetupProgressStatus;
    private TextView tvSetupEstimatedTime;
    private ProgressBar progressBarSetup;
    private TextView btnStartSetup;

    private SettingsManager settings;
    private boolean isSetupStarted = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        settings = new SettingsManager(requireContext());
        try {
            settings.applyFontToViewTree(view);
        } catch (Exception ignored) {}

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateSystemBarsAndTheme(android.graphics.Color.parseColor("#020A1F"));
        }

        cbEcommerce = view.findViewById(R.id.cbEcommerce);
        cbBanking   = view.findViewById(R.id.cbBanking);
        cbSchool    = view.findViewById(R.id.cbSchool);
        cbSocial    = view.findViewById(R.id.cbSocial);

        layoutProgressSection = view.findViewById(R.id.layoutProgressSection);
        tvSetupProgressStatus = view.findViewById(R.id.tvSetupProgressStatus);
        tvSetupEstimatedTime  = view.findViewById(R.id.tvSetupEstimatedTime);
        progressBarSetup      = view.findViewById(R.id.progressBarSetup);
        btnStartSetup         = view.findViewById(R.id.btnStartSetup);

        // Ecommerce is mandatory - always keep checked
        View.OnClickListener ecommerceLockListener = v -> {
            cbEcommerce.setChecked(true);
            try {
                android.widget.Toast.makeText(requireContext(), "ecommerce is required and cannot be unselected", android.widget.Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
        };
        view.findViewById(R.id.cardEcommerce).setOnClickListener(ecommerceLockListener);
        cbEcommerce.setOnClickListener(ecommerceLockListener);

        // Click on entire card toggles the checkbox
        view.findViewById(R.id.cardBanking).setOnClickListener(v -> cbBanking.setChecked(!cbBanking.isChecked()));
        view.findViewById(R.id.cardSchool).setOnClickListener(v -> cbSchool.setChecked(!cbSchool.isChecked()));
        view.findViewById(R.id.cardSocial).setOnClickListener(v -> cbSocial.setChecked(!cbSocial.isChecked()));

        btnStartSetup.setOnClickListener(v -> startDatabaseInstallation());
    }

    private void startDatabaseInstallation() {
        if (isSetupStarted) return;
        isSetupStarted = true;

        // Disable input
        btnStartSetup.setEnabled(false);
        btnStartSetup.setAlpha(0.6f);
        btnStartSetup.setText("Configuring Databases...");
        cbBanking.setEnabled(false);
        cbSchool.setEnabled(false);
        cbSocial.setEnabled(false);

        // Show progress section
        layoutProgressSection.setVisibility(View.VISIBLE);
        progressBarSetup.setProgress(5);
        tvSetupProgressStatus.setText("Starting database initialization...");
        if (tvSetupEstimatedTime != null) {
            tvSetupEstimatedTime.setText("⏱ ~8s");
        }

        // Collect selected databases (ecommerce is always required)
        List<String> selectedDatabases = new ArrayList<>();
        selectedDatabases.add("ecommerce");
        if (cbBanking.isChecked()) selectedDatabases.add("banking");
        if (cbSchool.isChecked()) selectedDatabases.add("school");
        if (cbSocial.isChecked()) selectedDatabases.add("social");

        SqlApiHelper.initializeSelectedDatabases(selectedDatabases, this);
    }

    @Override
    public void onSetupProgress(String currentDb, int currentDbIndex, int totalDbs, int progressPercent, String statusMessage, int estimatedSecondsRemaining) {
        if (!isAdded() || getActivity() == null) return;

        SqlThreadScheduler.runOnMainThread(() -> {
            if (progressBarSetup != null) {
                int p = Math.max(5, progressPercent);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    progressBarSetup.setProgress(p, true);
                } else {
                    progressBarSetup.setProgress(p);
                }
            }
            if (tvSetupProgressStatus != null && statusMessage != null) {
                tvSetupProgressStatus.setText(statusMessage);
            }
            if (tvSetupEstimatedTime != null) {
                if (estimatedSecondsRemaining > 0) {
                    tvSetupEstimatedTime.setText("⏱ ~" + estimatedSecondsRemaining + "s left");
                } else {
                    tvSetupEstimatedTime.setText("⏱ Finishing...");
                }
            }
        });
    }

    @Override
    public void onSetupCompleted() {
        if (!isAdded() || getActivity() == null) return;

        SqlThreadScheduler.runOnMainThread(() -> {
            if (progressBarSetup != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    progressBarSetup.setProgress(100, true);
                } else {
                    progressBarSetup.setProgress(100);
                }
            }
            if (tvSetupProgressStatus != null) {
                tvSetupProgressStatus.setText("Setup completed! Starting PocketSQL...");
            }
            if (tvSetupEstimatedTime != null) {
                tvSetupEstimatedTime.setText("✔ Done");
            }

            if (settings != null) {
                settings.setSetupCompleted(true);
            }

            SqlApiHelper.removeSetupListener(this);

            SqlThreadScheduler.postDelayed(() -> {
                if (isAdded() && getActivity() != null) {
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.main_container, new HomeFragment())
                            .commitAllowingStateLoss();
                }
            }, 600);
        });
    }

    @Override
    public void onDestroyView() {
        SqlApiHelper.removeSetupListener(this);
        super.onDestroyView();
    }
}
