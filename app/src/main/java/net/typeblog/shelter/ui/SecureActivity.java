package net.typeblog.shelter.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * SECURITY-CRITICAL BASE ACTIVITY
 *
 * This class is the mandatory security boundary for every Shelter Activity
 * that can display Shelter UI.
 *
 * SECURITY REQUIREMENTS:
 *
 * 1. Every Shelter Activity that displays application UI MUST extend
 *    SecureActivity. Do NOT extend Activity, FragmentActivity,
 *    AppCompatActivity, or another Activity class directly.
 *
 * 2. The Activity Window MUST remain protected by FLAG_SECURE.
 *
 * 3. Shelter UI MUST NOT be exposed through screenshots, screen recording,
 *    screen sharing, casting, or other screen-capture mechanisms that honor
 *    Android's secure-window policy.
 *
 * 4. Do NOT remove FLAG_SECURE from this class and do NOT replace this
 *    security mechanism with Activity-specific implementations.
 *
 * 5. If a new Activity is added to Shelter, it MUST extend SecureActivity.
 *    This is a mandatory security requirement, not an optional convenience.
 *
 * 6. Fragments do not require their own FLAG_SECURE because they do not own
 *    an independent Window. A Fragment displayed by a SecureActivity is
 *    protected by the Activity's secure Window.
 *
 * 7. Dialogs created by Shelter have their own Window and therefore MUST
 *    also be explicitly secured with FLAG_SECURE. Use secureDialog(...)
 *    for Shelter-created Dialog instances.
 *
 * SECURITY GOAL:
 *
 * No Shelter-owned Activity UI or Shelter-created Dialog UI may be exposed
 * through screenshot, screen recording, or screen-sharing capture.
 *
 * IMPORTANT:
 * Android/system-owned authentication UI such as BiometricPrompt is
 * controlled by the Android system and is not a Shelter-owned Dialog.
 */
public abstract class SecureActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        /*
         * SECURITY-CRITICAL:
         *
         * FLAG_SECURE MUST be installed before Shelter renders its UI.
         * Do not remove, weaken, or move this protection to individual
         * Activities.
         */
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SECURE
        );

        super.onCreate(savedInstanceState);
    }

    /**
     * Applies the same mandatory screenshot / recording protection to
     * a Dialog owned by Shelter.
     *
     * Every Shelter-created Dialog with its own Window MUST use this
     * method before it is displayed.
     */
    public static void secureDialog(@Nullable Dialog dialog) {
        if (dialog == null) {
            return;
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SECURE
            );
        }
    }
}
