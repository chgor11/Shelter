package net.typeblog.shelter.ui;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import net.typeblog.shelter.R;

import java.util.concurrent.Executor;

public class SettingsActivity extends AppCompatActivity {

    private CancellationSignal cancellationSignal;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            EdgeToEdge.enable(this);
        }

        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            authenticateForSettings();
        } else {
            finish();
        }
    }

    private void authenticateForSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            finish();
            return;
        }

        KeyguardManager keyguardManager =
                (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        if (keyguardManager == null
                || !keyguardManager.isKeyguardSecure()) {
            finish();
            return;
        }

        Executor executor = getMainExecutor();

        BiometricPrompt.AuthenticationCallback callback =
                new BiometricPrompt.AuthenticationCallback() {

                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        showSettings();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                    }

                    @Override
                    public void onAuthenticationError(
                            int errorCode,
                            CharSequence errString) {
                        super.onAuthenticationError(
                                errorCode,
                                errString
                        );

                        finish();
                    }
                };

        BiometricPrompt biometricPrompt =
                new BiometricPrompt.Builder(this)
                        .setTitle("Authentication required")
                        .setSubtitle(
                                "Authenticate to open Shelter settings"
                        )
                        .setNegativeButton(
                                "Cancel",
                                executor,
                                (dialog, which) -> finish()
                        )
                        .build();

        cancellationSignal = new CancellationSignal();

        biometricPrompt.authenticate(
                cancellationSignal,
                executor,
                callback
        );
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
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        if (cancellationSignal != null) {
            cancellationSignal.cancel();
            cancellationSignal = null;
        }

        super.onDestroy();
    }
}
