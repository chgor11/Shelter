package net.typeblog.shelter.ui;

import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import net.typeblog.shelter.R;

public class SettingsActivity extends SecureActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);

        super.onCreate(savedInstanceState);

        authenticateForSettings();
    }

    private void authenticateForSettings() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

        if (biometricManager.canAuthenticate(authenticators)
                != BiometricManager.BIOMETRIC_SUCCESS) {
            finish();
            return;
        }

        BiometricPrompt prompt = new BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {
                        showSettings();
                    }

                    @Override
                    public void onAuthenticationError(
                            int errorCode, CharSequence errString) {
                        finish();
                    }
                }
        );

        BiometricPrompt.PromptInfo promptInfo =
                new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Authentication required")
                        .setSubtitle("Enter your device credential to open Shelter settings")
                        .setAllowedAuthenticators(authenticators)
                        .build();

        prompt.authenticate(promptInfo);
    }

    private void showSettings() {
        setContentView(R.layout.activity_settings);

        setSupportActionBar(
                findViewById(R.id.settings_toolbar)
        );

        if (getSupportActionBar() != null) {
            getSupportActionBar()
                    .setDisplayHomeAsUpEnabled(true);
        }

        Fragment existingFragment =
                getSupportFragmentManager()
                        .findFragmentByTag("settings_fragment");

        if (existingFragment == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.settings_fragment_container,
                            new SettingsFragment(),
                            "settings_fragment"
                    )
                    .commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager()
                .getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            return true;
        }
        finish();
        return true;
    }
}
