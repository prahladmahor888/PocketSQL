package com.mysql.pocketsql;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SplashFragment extends Fragment {

    private static final long MIN_SPLASH_DURATION_MS = 3000L; // Minimum 3 seconds display

    private TextView tvSplashStatus;
    private TextView tvSplashWaitTime;
    private ProgressBar splashProgressHorizontal;
    private boolean isNavigated = false;
    private long splashStartTime = 0L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_splash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        splashStartTime = System.currentTimeMillis();

        tvSplashStatus = view.findViewById(R.id.tvSplashStatus);
        tvSplashWaitTime = view.findViewById(R.id.tvSplashWaitTime);
        splashProgressHorizontal = view.findViewById(R.id.splash_progress_horizontal);

        try {
            SettingsManager settings = new SettingsManager(requireContext());
            settings.applyFontToViewTree(view);
        } catch (Exception ignored) {}

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateSystemBarsAndTheme(android.graphics.Color.parseColor("#020A1F"));
        }

        // Display smooth progress on splash
        if (splashProgressHorizontal != null) {
            splashProgressHorizontal.setProgress(50);
        }
        if (tvSplashStatus != null) {
            tvSplashStatus.setText("PocketSQL Server Starting...");
        }
        if (tvSplashWaitTime != null) {
            tvSplashWaitTime.setText("Preparing environment • Please wait...");
        }

        // Schedule minimum 3-second transition
        handler.postDelayed(this::checkAndNavigateNext, MIN_SPLASH_DURATION_MS);
    }

    private synchronized void checkAndNavigateNext() {
        if (isNavigated || !isAdded() || getActivity() == null) return;
        isNavigated = true;

        handler.removeCallbacksAndMessages(null);

        SettingsManager settings = new SettingsManager(requireContext());
        boolean isSetupDone = settings.isSetupCompleted();

        Fragment nextFragment = isSetupDone ? new HomeFragment() : new SetupFragment();

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_container, nextFragment)
                .commitAllowingStateLoss();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
