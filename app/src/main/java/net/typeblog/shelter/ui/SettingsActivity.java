package net.typeblog.shelter.ui;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import net.typeblog.shelter.R;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import java.util.concurrent.Executor;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    
        authenticateForSettings();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

private void authenticateForSettings() {
    BiometricManager biometricManager =
            getSystemService(BiometricManager.class);

    int result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    );

    if (result != BiometricManager.BIOMETRIC_SUCCESS) {
        finish();
        return;
    }

    Executor executor = getMainExecutor();

    BiometricPrompt biometricPrompt =
            new BiometricPrompt(
                    this,
                    executor,
                    new BiometricPrompt.AuthenticationCallback() {

                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);

                            showSettings();
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

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                        }
                    }
            );

    BiometricPrompt.PromptInfo promptInfo =
            new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Authentication required")
                    .setSubtitle("Enter your device credential to open Shelter settings")
                    .setAllowedAuthenticators(
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build();

    biometricPrompt.authenticate(promptInfo);
}

private void showSettings() {
    setContentView(R.layout.activity_settings);
}
