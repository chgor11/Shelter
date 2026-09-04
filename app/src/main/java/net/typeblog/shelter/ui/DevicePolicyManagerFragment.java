package net.typeblog.shelter.ui;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Bundle;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import net.typeblog.shelter.R;
import net.typeblog.shelter.util.SecurityPolicyChangeManager;

/*
 * SECURITY POLICY ADMINISTRATION RULE
 *
 * Any policy added to this screen MUST follow this flow:
 *
 * 1. Never apply immediately.
 * 2. Store as pending change only.
 * 3. Show complete change summary.
 * 4. Require explicit confirmation.
 * 5. Require fresh authentication.
 * 6. Apply only after successful authentication.
 *
 * Adding direct DevicePolicyManager calls here without this flow
 * is forbidden.
 */

public class DevicePolicyManagerFragment
        extends PreferenceFragmentCompat {

    private static final int REQUEST_POLICY_AUTH = 9001;

    private static final String PREF_DEVICE_LOCK_DELAY =
            "device_lock_delay";

    private Preference mApplyPreference;

    private SecurityPolicyChangeManager mChangeManager;


    @Override
    public void onCreatePreferences(
            Bundle savedInstanceState,
            String rootKey) {

        addPreferencesFromResource(
                R.xml.device_policy_preferences
        );

        mChangeManager =
                SecurityPolicyChangeManager
                        .getInstance(requireContext());


        EditTextPreference lockDelay =
                findPreference(
                        PREF_DEVICE_LOCK_DELAY
                );


        if (lockDelay != null) {

            lockDelay.setOnPreferenceChangeListener(
                    (preference, newValue) -> {

                        /*
                         * IMPORTANT:
                         *
                         * This does NOT apply the policy.
                         * It only creates a pending transaction.
                         */

                        mChangeManager.addPendingChange(
                                "DEVICE_LOCK_DELAY",
                                lockDelay.getText(),
                                String.valueOf(newValue)
                        );

                        return true;
                    });
        }


        mApplyPreference =
                findPreference(
                        "apply_security_changes"
                );


        if (mApplyPreference != null) {

            mApplyPreference.setOnPreferenceClickListener(
                    preference -> {

                        showPendingChanges();

                        return true;
                    });
        }
    }



    private void showPendingChanges() {

        String summary =
                mChangeManager
                        .getPendingChangesSummary();


        if (summary == null ||
                summary.isEmpty()) {

            new AlertDialog.Builder(requireContext())
                    .setTitle(
                            "No Pending Changes"
                    )
                    .setMessage(
                            "There are no security changes waiting to be applied."
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();

            return;
        }


        new AlertDialog.Builder(requireContext())
                .setTitle(
                        "Confirm Security Changes"
                )
                .setMessage(summary)
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Confirm",
                        (dialog, which) -> {

                            requestAuthentication();

                        })
                .show();
    }



    private void requestAuthentication() {


        KeyguardManager keyguardManager =
                (KeyguardManager)
                        requireContext()
                                .getSystemService(
                                        Context.KEYGUARD_SERVICE
                                );


        if (keyguardManager == null ||
                !keyguardManager.isKeyguardSecure()) {


            return;
        }


        Intent intent =
                keyguardManager
                        .createConfirmDeviceCredentialIntent(
                                "Authentication required",
                                "Authenticate to apply security changes"
                        );


        if (intent != null) {

            startActivityForResult(
                    intent,
                    REQUEST_POLICY_AUTH
            );
        }
    }



    @Override
    public void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );


        if (requestCode ==
                REQUEST_POLICY_AUTH) {


            if (resultCode ==
                    android.app.Activity.RESULT_OK) {


                /*
                 * Authentication is valid only
                 * for this current transaction.
                 */

                mChangeManager
                        .applyAuthenticatedChanges();


            } else {


                mChangeManager
                        .clearAuthenticationSession();

            }
        }
    }
}
