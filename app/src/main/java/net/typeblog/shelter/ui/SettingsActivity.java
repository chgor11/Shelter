package net.typeblog.shelter.ui;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import net.typeblog.shelter.R;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_DEVICE_CREDENTIAL = 1001;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            EdgeToEdge.enable(this);
        }

        super.onCreate(savedInstanceState);

        authenticateForSettings();
    }

    private void authenticateForSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            finish();
            return;
        }

        KeyguardManager keyguardManager =
                (KeyguardManager) getSystemService(
                        Context.KEYGUARD_SERVICE
                );

        if (keyguardManager == null
                || !keyguardManager.isKeyguardSecure()) {
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent =
                    keyguardManager.createConfirmDeviceCredentialIntent(
                            "Authentication required",
                            "Enter your device credential to open Shelter settings"
                    );

            if (intent == null) {
                finish();
                return;
            }

            startActivityForResult(
                    intent,
                    REQUEST_DEVICE_CREDENTIAL
            );
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == REQUEST_DEVICE_CREDENTIAL) {

            if (resultCode == RESULT_OK) {
                showSettings();
            } else {
                finish();
            }
        }
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
}
