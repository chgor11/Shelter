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

    private static final String PREF_APPLY_MAXIMUM_WORK_PROFILE_SECURITY =
            "apply_maximum_work_profile_security";

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
                             * SECURITY:
                             *
                             * This click ONLY queues the action.
                             *
                             * The device MUST NOT be locked here.
                             *
                             * The actual lock operation is performed by
                             * SecurityPolicyChangeManager only after:
                             *
                             * 1. The user reviews the pending transaction.
                             * 2. The user explicitly confirms it.
                             * 3. Successful system device-credential authentication.
                             *
                             * TemporaryActionPreference.onClick() has already
                             * changed the visual state to "pressed", therefore
                             * isPressed() MUST NOT be used as the condition for
                             * registering the pending action here.
                             */
                            mChangeManager.addPendingAction(
                                    ACTION_LOCK_PHONE_NOW
                            );
                
                            return true;
                    }
            );
        }

        /*
         * ============================================================
         * PERMANENT MAXIMUM WORK PROFILE SECURITY
         * ============================================================
         *
         * This is intentionally implemented as a TemporaryActionPreference,
         * not as a toggle. Pressing it can only queue an APPLY action.
         *
         * There is deliberately no "disable" action in the UI or in the
         * transaction manager.
         */
        TemporaryActionPreference maximumSecurity =
                findPreference(
                        PREF_APPLY_MAXIMUM_WORK_PROFILE_SECURITY
                );

        if (maximumSecurity != null) {

            maximumSecurity.reset();

            maximumSecurity.setOnPreferenceClickListener(
                    preference -> {

                        /*
                         * SECURITY:
                         *
                         * Do not use isPressed() here. TemporaryActionPreference
                         * has already set its visual state to pressed before
                         * the click listener runs.
                         */
                        mChangeManager.addPendingAction(
                                DummyActivity.APPLY_MAXIMUM_WORK_PROFILE_SECURITY
                        );

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
        
            /*
             * SECURITY POLICY AUTHENTICATION
             *
             * IMPORTANT:
             *
             * This authentication belongs to the MAIN / PERSONAL profile.
             *
             * It is intentionally NOT Work Profile authentication.
             *
             * Only the device credential of the current/main profile
             * may authorize application of pending security-policy changes:
             *
             *     - PIN
             *     - Pattern
             *     - Password
             *
             * DO NOT replace this flow with BiometricPrompt.
             *
             * In particular, do NOT use:
             *
             *     BIOMETRIC_STRONG
             *     BIOMETRIC_WEAK
             *     BIOMETRIC_STRONG | DEVICE_CREDENTIAL
             *
             * Fingerprint and Face Unlock must not authorize this
             * security-policy transaction.
             *
             * Successful authorization is accepted only when the
             * Android device-credential confirmation activity returns
             * RESULT_OK.
             *
             * This is intentionally the same authentication mechanism
             * used when opening Shelter Settings.
             */
        
            KeyguardManager keyguardManager =
                    (KeyguardManager)
                            requireContext()
                                    .getSystemService(
                                            Context.KEYGUARD_SERVICE
                                    );
        
            /*
             * The MAIN / PERSONAL profile must have a secure
             * device credential configured.
             */
            if (keyguardManager == null
                    || !keyguardManager.isKeyguardSecure()) {
        
                /*
                 * Never leave an authenticated transaction pending
                 * when credential authentication is unavailable.
                 */
                mChangeManager.clearAuthenticationSession();
        
                new AlertDialog.Builder(requireContext())
                        .setTitle("Authentication Required")
                        .setMessage(
                                "A device PIN, pattern, or password must be configured before security changes can be applied."
                        )
                        .setPositiveButton(
                                android.R.string.ok,
                                null
                        )
                        .show();
        
                return;
            }
        
            /*
             * Request Android's DEVICE CREDENTIAL confirmation UI.
             *
             * This is deliberately NOT BiometricPrompt.
             *
             * The intent is created from the KeyguardManager associated
             * with this Activity's Context, therefore the authentication
             * is for the current/main profile rather than the Work Profile.
             */
            Intent intent =
                    keyguardManager.createConfirmDeviceCredentialIntent(
                            "Authentication required",
                            "Enter your device credential to apply security changes"
                    );
        
            /*
             * Android may be unable to create the credential-confirmation
             * activity. In that case the pending transaction must remain
             * unauthorized.
             */
            if (intent == null) {
        
                mChangeManager.clearAuthenticationSession();
        
                new AlertDialog.Builder(requireContext())
                        .setTitle("Authentication Unavailable")
                        .setMessage(
                                "Device credential authentication is currently unavailable."
                        )
                        .setPositiveButton(
                                android.R.string.ok,
                                null
                        )
                        .show();
        
                return;
            }
        
            /*
             * IMPORTANT:
             *
             * Do not authorize anything here.
             *
             * Authorization occurs only in onActivityResult() after
             * Android reports RESULT_OK for REQUEST_POLICY_AUTH.
             */
            startActivityForResult(
                    intent,
                    REQUEST_POLICY_AUTH
            );
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
