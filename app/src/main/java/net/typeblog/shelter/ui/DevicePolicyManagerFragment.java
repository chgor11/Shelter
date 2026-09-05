package net.typeblog.shelter.ui;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

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
 *
 * TEMPORARY ACTION RULE:
 *
 * Temporary action preferences are UI-only transaction selectors.
 * They do not execute security operations.
 *
 * Their state:
 *
 *     NOT PRESSED
 *          ↓
 *       PRESS
 *          ↓
 *       PRESSED
 *
 * remains only for the current page/transaction.
 *
 * The actual operation is executed exclusively by
 * SecurityPolicyChangeManager after authentication.
 */

public class DevicePolicyManagerFragment
        extends PreferenceFragmentCompat {

    private static final int REQUEST_POLICY_AUTH = 9001;

    private static final String PREF_DEVICE_LOCK_DELAY =
            "device_lock_delay";

    private static final String PREF_LOCK_PHONE_NOW =
            "lock_phone_now";

    private static final String ACTION_LOCK_PHONE_NOW =
            "LOCK_PHONE_NOW";

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

        /*
         * ============================================================
         * DEVICE LOCK DELAY
         * ============================================================
         */

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

                        String newDelay =
                                String.valueOf(newValue);

                        /*
                         * SECURITY POLICY VALIDATION RULE:
                         *
                         * Every security policy input MUST
                         * be validated before becoming a
                         * pending transaction.
                         */

                        int delaySeconds;

                        try {

                            delaySeconds =
                                    Integer.parseInt(
                                            newDelay
                                    );

                        } catch (NumberFormatException e) {

                            AlertDialog dialog =
                                    new AlertDialog.Builder(
                                            requireContext()
                                    )
                                            .setTitle(
                                                    "Invalid Value"
                                            )
                                            .setMessage(
                                                    "Device Lock Delay must be a valid number."
                                            )
                                            .setPositiveButton(
                                                    "OK",
                                                    null
                                            )
                                            .create();

                            SecureActivity.secureDialog(
                                    dialog
                            );

                            dialog.show();

                            return false;
                        }

                        /*
                         * -1 is valid:
                         *
                         * -1 = do nothing / never lock.
                         */
                        if (delaySeconds < -1) {

                            AlertDialog dialog =
                                    new AlertDialog.Builder(
                                            requireContext()
                                    )
                                            .setTitle(
                                                    "Invalid Value"
                                            )
                                            .setMessage(
                                                    "Device Lock Delay cannot be less than -1."
                                            )
                                            .setPositiveButton(
                                                    "OK",
                                                    null
                                            )
                                            .create();

                            SecureActivity.secureDialog(
                                    dialog
                            );

                            dialog.show();

                            return false;
                        }

                        /*
                         * Maximum delay: 24 hours.
                         */
                        if (delaySeconds > 86400) {

                            AlertDialog dialog =
                                    new AlertDialog.Builder(
                                            requireContext()
                                    )
                                            .setTitle(
                                                    "Invalid Value"
                                            )
                                            .setMessage(
                                                    "Device Lock Delay cannot exceed 86400 seconds."
                                            )
                                            .setPositiveButton(
                                                    "OK",
                                                    null
                                            )
                                            .create();

                            SecureActivity.secureDialog(
                                    dialog
                            );

                            dialog.show();

                            return false;
                        }

                        String oldValue =
                                lockDelay.getText();

                        if (oldValue == null ||
                                oldValue.isEmpty()) {

                            oldValue = "-1";
                        }

                        /*
                         * Preference changes are NEVER applied here.
                         *
                         * They only create pending transactions.
                         */
                        mChangeManager.addPendingChange(
                                "DEVICE_LOCK_DELAY",
                                oldValue,
                                newDelay
                        );

                        /*
                         * Do not persist this Preference value.
                         */
                        return false;
                    }
            );
        }

        /*
         * ============================================================
         * LOCK PHONE NOW
         * ============================================================
         */

        TemporaryActionPreference lockPhoneNow =
                findPreference(
                        PREF_LOCK_PHONE_NOW
                );

        if (lockPhoneNow != null) {

            /*
             * SECURITY:
             *
             * This preference is always transient.
             *
             * A newly opened page starts unpressed.
             */
            lockPhoneNow.reset();

            lockPhoneNow.setOnPreferenceClickListener(
                    preference -> {

                        /*
                         * Pressing the key creates ONLY a pending
                         * action.
                         *
                         * NO lockNow() is executed here.
                         */
                        if (!lockPhoneNow.isPressed()) {

                            lockPhoneNow.press();

                            mChangeManager.addPendingAction(
                                    ACTION_LOCK_PHONE_NOW
                            );
                        }

                        return true;
                    }
            );
        }

        /*
         * ============================================================
         * APPLY SECURITY CHANGES
         * ============================================================
         */

        mApplyPreference =
                findPreference(
                        "apply_security_changes"
                );

        if (mApplyPreference != null) {

            mApplyPreference.setOnPreferenceClickListener(
                    preference -> {

                        showPendingChanges();

                        return true;
                    }
            );
        }
    }

    /*
     * ============================================================
     * REVIEW
     * ============================================================
     */

    private void showPendingChanges() {

        String summary =
                mChangeManager
                        .getPendingChangesSummary();

        if (summary == null ||
                summary.isEmpty()) {

            AlertDialog dialog =
                    new AlertDialog.Builder(
                            requireContext()
                    )
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
                            .create();

            SecureActivity.secureDialog(dialog);

            dialog.show();

            return;
        }

        AlertDialog dialog =
                new AlertDialog.Builder(
                        requireContext()
                )
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
                                (dialogInterface, which) -> {

                                    requestAuthentication();

                                })
                        .create();

        SecureActivity.secureDialog(dialog);

        dialog.show();
    }

    /*
     * ============================================================
     * AUTHENTICATION
     * ============================================================
     */

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
                        .authorizeCurrentTransaction();

                /*
                 * This is the ONLY point where the
                 * pending security operations are allowed
                 * to execute.
                 */
                mChangeManager
                        .applyAuthenticatedChanges();

            } else {

                /*
                 * Authentication failed/cancelled.
                 *
                 * No pending operation is executed.
                 */
                mChangeManager
                        .clearAuthenticationSession();
            }
        }
    }

    /*
     * ============================================================
     * PAGE LIFECYCLE
     * ============================================================
     */

    @Override
    public void onDestroyView() {

        /*
         * SECURITY:
         *
         * Pending actions belong only to this page/transaction.
         *
         * If the page is closed without successful application,
         * all uncommitted actions disappear.
         *
         * Already-applied policies are NOT reverted here.
         */
        if (mChangeManager != null) {

            mChangeManager
                    .clearPendingTransaction();
        }

        super.onDestroyView();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(
                view,
                savedInstanceState
        );

        /*
         * Make the entire Security Policy page opaque.
         */
        view.setBackgroundColor(
                android.graphics.Color.BLACK
        );

        View recyclerView =
                view.findViewById(
                        androidx.preference.R.id.recycler_view
                );

        if (recyclerView != null) {

            recyclerView.setBackgroundColor(
                    android.graphics.Color.BLACK
            );
        }
    }
}
