package com.mysql.pocketsql;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SplashFragment extends Fragment {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable navigateRunnable = () -> {
        if (isAdded()) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_container, new HomeFragment())
                    .commit();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_splash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        handler.postDelayed(navigateRunnable, 1500);
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(navigateRunnable);
        super.onDestroyView();
    }
}
