package com.drdisagree.colorblendr.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.drdisagree.colorblendr.ColorBlendr;
import com.drdisagree.colorblendr.R;
import com.drdisagree.colorblendr.common.Const;
import com.drdisagree.colorblendr.databinding.FragmentHomeBinding;
import com.drdisagree.colorblendr.service.BackgroundService;
import com.drdisagree.colorblendr.utils.AppUtil;
import com.google.android.material.snackbar.Snackbar;

import java.util.Map;
import java.util.Objects;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private static Fragment currentFragment;
    private static FragmentManager fragmentManager;

    public enum TAB_SELECTION {
        FROM_LEFT_TO_RIGHT,
        FROM_RIGHT_TO_LEFT,
        NONE
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        fragmentManager = getChildFragmentManager();

        if (savedInstanceState == null) {
            replaceFragment(new ColorsFragment());
        }

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupBottomNavigationView();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (AppUtil.permissionsGranted(requireContext())) {
                    if (!Const.isBackgroundServiceRunning) {
                        requireContext().startService(new Intent(ColorBlendr.getAppContext(), BackgroundService.class));
                    }
                } else {
                    requestPermissionsLauncher.launch(AppUtil.REQUIRED_PERMISSIONS);
                }
            } catch (Exception ignored) {
            }
        }, 2000);

        registerOnBackPressedCallback();
    }

    public static void replaceFragment(Fragment fragment) {
        String tag = fragment.getClass().getSimpleName();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        TAB_SELECTION direction = getSlidingDirection(currentFragment, fragment);

        if (currentFragment != null) {
            if (direction == TAB_SELECTION.FROM_LEFT_TO_RIGHT) {
                fragmentTransaction.setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                );
            } else if (direction == TAB_SELECTION.FROM_RIGHT_TO_LEFT) {
                fragmentTransaction.setCustomAnimations(
                        R.anim.slide_in_left,
                        R.anim.slide_out_right,
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                );
            } else {
                fragmentTransaction.setCustomAnimations(
                        R.anim.fade_in,
                        R.anim.fade_out,
                        R.anim.fade_in,
                        R.anim.fade_out
                );
            }
        }

        fragmentTransaction.replace(
                R.id.fragmentContainer,
                fragment
        );

        if (Objects.equals(tag, ColorsFragment.class.getSimpleName())) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } else if (Objects.equals(tag, ThemeFragment.class.getSimpleName()) ||
                Objects.equals(tag, StylesFragment.class.getSimpleName()) ||
                Objects.equals(tag, SettingsFragment.class.getSimpleName())
        ) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            fragmentTransaction.addToBackStack(tag);
        } else {
            fragmentTransaction.addToBackStack(tag);
        }

        fragmentTransaction.commit();
        currentFragment = fragment;
    }

    private void setupBottomNavigationView() {
        getChildFragmentManager().addOnBackStackChangedListener(() -> {
            String tag = getTopFragment();

            if (Objects.equals(tag, ColorsFragment.class.getSimpleName())) {
                binding.bottomNavigationView.getMenu().getItem(0).setChecked(true);
            } else if (Objects.equals(tag, ThemeFragment.class.getSimpleName())) {
                binding.bottomNavigationView.getMenu().getItem(1).setChecked(true);
            } else if (Objects.equals(tag, StylesFragment.class.getSimpleName())) {
                binding.bottomNavigationView.getMenu().getItem(2).setChecked(true);
            } else if (Objects.equals(tag, SettingsFragment.class.getSimpleName())) {
                binding.bottomNavigationView.getMenu().getItem(3).setChecked(true);
            }
        });

        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_colors) {
                replaceFragment(new ColorsFragment());
            } else if (item.getItemId() == R.id.nav_themes) {
                replaceFragment(new ThemeFragment());
            } else if (item.getItemId() == R.id.nav_styles) {
                replaceFragment(new StylesFragment());
            } else if (item.getItemId() == R.id.nav_settings) {
                replaceFragment(new SettingsFragment());
            } else {
                return false;
            }

            return true;
        });

        binding.bottomNavigationView.setOnItemReselectedListener(item -> {
            // Do nothing
        });
    }

    private void registerOnBackPressedCallback() {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fragmentManager = getChildFragmentManager();
                if (fragmentManager.getBackStackEntryCount() > 0) {
                    fragmentManager.popBackStack();
                } else {
                    requireActivity().finish();
                }
            }
        });
    }

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            this::handlePermissionsResult
    );

    private void handlePermissionsResult(Map<String, Boolean> result) {
        for (Map.Entry<String, Boolean> pair : result.entrySet()) {
            if (!pair.getValue()) {
                showGeneralPermissionSnackbar(pair.getKey());
                return;
            }
        }

        if (!AppUtil.hasStoragePermission()) {
            showStoragePermissionSnackbar();
            return;
        }

        if (!Const.isBackgroundServiceRunning) {
            requireContext().startService(new Intent(ColorBlendr.getAppContext(), BackgroundService.class));
        }
    }

    private void showGeneralPermissionSnackbar(String permission) {
        Snackbar snackbar = Snackbar.make(
                requireActivity().findViewById(android.R.id.content),
                R.string.permission_must_be_granted,
                Snackbar.LENGTH_INDEFINITE
        );
        snackbar.setAction(R.string.grant, v -> {
            requestPermissionsLauncher.launch(new String[]{permission});
            snackbar.dismiss();
        });
        snackbar.show();
    }

    private void showStoragePermissionSnackbar() {
        Snackbar snackbar = Snackbar.make(
                requireActivity().findViewById(android.R.id.content),
                R.string.file_access_permission_required,
                Snackbar.LENGTH_INDEFINITE
        );
        snackbar.setAction(R.string.grant, v -> {
            AppUtil.requestStoragePermission(requireContext());
            snackbar.dismiss();
        });
        snackbar.show();
    }

    private static TAB_SELECTION getSlidingDirection(Fragment currentFragment, Fragment newFragment) {
        if (currentFragment == null) {
            return TAB_SELECTION.NONE;
        }

        boolean reverseAnimation;

        if (currentFragment instanceof ColorsFragment &&
                (newFragment instanceof ThemeFragment || newFragment instanceof StylesFragment || newFragment instanceof SettingsFragment)
        ) {
            reverseAnimation = false;
        } else if (currentFragment instanceof SettingsFragment &&
                (newFragment instanceof ThemeFragment || newFragment instanceof StylesFragment || newFragment instanceof ColorsFragment)
        ) {
            reverseAnimation = true;
        } else if (currentFragment instanceof ThemeFragment) {
            if (newFragment instanceof ColorsFragment) {
                reverseAnimation = true;
            } else if (newFragment instanceof StylesFragment || newFragment instanceof SettingsFragment) {
                reverseAnimation = false;
            } else {
                return TAB_SELECTION.NONE;
            }
        } else if (currentFragment instanceof StylesFragment) {
            if (newFragment instanceof SettingsFragment) {
                reverseAnimation = false;
            } else if (newFragment instanceof ColorsFragment || newFragment instanceof ThemeFragment) {
                reverseAnimation = true;
            } else {
                return TAB_SELECTION.NONE;
            }
        } else {
            return TAB_SELECTION.NONE;
        }

        return reverseAnimation ? TAB_SELECTION.FROM_RIGHT_TO_LEFT : TAB_SELECTION.FROM_LEFT_TO_RIGHT;
    }

    private String getTopFragment() {
        String[] fragment = {null};

        FragmentManager fragmentManager = getChildFragmentManager();
        int last = fragmentManager.getFragments().size() - 1;

        if (last >= 0) {
            Fragment topFragment = fragmentManager.getFragments().get(last);
            currentFragment = topFragment;

            if (topFragment instanceof ColorsFragment)
                fragment[0] = ColorsFragment.class.getSimpleName();
            else if (topFragment instanceof ThemeFragment)
                fragment[0] = ThemeFragment.class.getSimpleName();
            else if (topFragment instanceof StylesFragment)
                fragment[0] = StylesFragment.class.getSimpleName();
            else if (topFragment instanceof SettingsFragment)
                fragment[0] = SettingsFragment.class.getSimpleName();
        }

        return fragment[0];
    }
}