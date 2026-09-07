package net.typeblog.shelter.util;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.ui.DummyActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * SECURITY POLICY CHANGE MANAGER
 *
 * THIS CLASS IS THE ONLY AUTHORIZED ENTRY POINT
 * FOR SECURITY POLICY APPLICATION.
 *
 * SECURITY RULES:
 *
 * 1. UI code MUST NEVER directly apply security policies.
 * 2. UI code may only create pending changes/actions.
 * 3. Pending operations require explicit user confirmation.
 * 4. Pending operations require fresh authentication.
 * 5. Operations are applied only after successful authentication.
 * 6. Authentication is one-time and destroyed immediately after use.
 * 7. Temporary actions are never persisted.
 *
 * Any new security action MUST follow this transaction:
 *
 *     UI
 *      ↓
 * Pending Action
 *      ↓
 * Review
 *      ↓
 * Explicit Confirmation
 *      ↓
 * Fresh Authentication
 *      ↓
 * Apply
 *
 * Direct DevicePolicyManager calls outside this class are forbidden
 * for security-policy operations.
 */
public class SecurityPolicyChangeManager {

    private static SecurityPolicyChangeManager instance;

    private static final String ACTION_LOCK_PHONE_NOW =
            "LOCK_PHONE_NOW";

    private final Context context;

    /*
     * Handler is used only for the already-existing delayed
     * device-lock policy.
     */
    private final Handler lockHandler =
            new Handler(Looper.getMainLooper());

    private Runnable pendingLockRunnable = null;

    /*
     * Normal value-changing policies.
     */
    private final Map<String, PendingSecurityChange>
            pendingChanges =
            new HashMap<>();

    /*
     * Temporary one-shot actions.
     *
     * These are NEVER persisted.
     */
    private final Map<String, PendingSecurityAction>
            pendingActions =
            new HashMap<>();

    /*
     * Created ONLY after successful authentication.
     *
     * This authorization is valid only for the current transaction.
     */
    private boolean authenticatedSession = false;

    private SecurityPolicyChangeManager(Context context) {

        this.context =
                context.getApplicationContext();
    }

    public static synchronized SecurityPolicyChangeManager
    getInstance(Context context) {

        if (instance == null) {

            instance =
                    new SecurityPolicyChangeManager(context);
        }

        return instance;
    }

    /*
     * ============================================================
     * PENDING VALUE CHANGES
     * ============================================================
     */

    public void addPendingChange(
            String policy,
            String oldValue,
            String newValue) {

        /*
         * IMPORTANT:
         *
         * NEVER APPLY POLICY HERE.
         *
         * Only create a pending transaction.
         */
        pendingChanges.put(
                policy,
                new PendingSecurityChange(
                        policy,
                        oldValue,
                        newValue
                )
        );
    }

    /*
     * ============================================================
     * PENDING TEMPORARY ACTIONS
     * ============================================================
     */

    /**
     * Adds a temporary one-shot action to the current transaction.
     *
     * The action is identified only by its ID.
     *
     * No security operation is executed here.
     */
    public void addPendingAction(String actionId) {

        if (actionId == null ||
                actionId.isEmpty()) {

            return;
        }

        pendingActions.put(
                actionId,
                new PendingSecurityAction(actionId)
        );
    }

    public boolean hasPendingAction(String actionId) {

        return pendingActions.containsKey(actionId);
    }

    public boolean hasPendingChanges() {

        return !pendingChanges.isEmpty()
                || !pendingActions.isEmpty();
    }

    /*
     * ============================================================
     * REVIEW SUMMARY
     * ============================================================
     */

    public String getPendingChangesSummary() {

        StringBuilder result =
                new StringBuilder();

        for (PendingSecurityChange change :
                pendingChanges.values()) {

            result.append(
                    change.getPolicyName()
            );

            result.append("\nOld value: ");

            result.append(
                    change.getOldValue()
            );

            result.append("\nNew value: ");

            result.append(
                    change.getNewValue()
            );

            result.append("\n\n");
        }

        for (PendingSecurityAction action :
                pendingActions.values()) {

            if (ACTION_LOCK_PHONE_NOW.equals(
                    action.getActionId())) {

                result.append(
                        "Action: Lock phone immediately"
                );

                result.append("\nStatus: Pressed");

                result.append("\n\n");
            }

            if (DummyActivity.APPLY_MAXIMUM_WORK_PROFILE_SECURITY.equals(
                    action.getActionId())) {

                result.append(
                        "Action: Permanently apply maximum Work Profile security"
                );

                result.append("\nStatus: Requested");

                result.append("\n\n");
            }
        }

        return result.toString();
    }

    /*
     * ============================================================
     * AUTHORIZATION
     * ============================================================
     */

    /**
     * Called ONLY after successful system authentication.
     */
    public void authorizeCurrentTransaction() {

        authenticatedSession = true;
    }

    /*
     * ============================================================
     * APPLY
     * ============================================================
     */

    /**
     * Applies the complete current transaction.
     *
     * Nothing is executed unless a fresh authentication
     * has first authorized this transaction.
     */
    public void applyAuthenticatedChanges() {

        /*
         * SECURITY CHECK:
         *
         * Applying without fresh authentication
         * is forbidden.
         */
        if (!authenticatedSession) {

            return;
        }

        /*
         * Apply normal policy changes.
         */
        for (PendingSecurityChange change :
                pendingChanges.values()) {

            if ("DEVICE_LOCK_DELAY"
                    .equals(change.getPolicyName())) {

                applyDeviceLockDelay(
                        Integer.parseInt(
                                change.getNewValue()
                        )
                );
            }
        }

        /*
         * Apply temporary one-shot actions.
         */
        for (PendingSecurityAction action :
                new ArrayList<>(
                        pendingActions.values())) {

            /*
             * A cross-profile request can fail before it reaches the
             * Work Profile (for example, if the profile is unavailable).
             * In that case the action MUST remain pending so that the
             * user can retry after the profile becomes available.
             * A successfully dispatched request is removed below.
             */
            if (applyPendingAction(action.getActionId())) {
                pendingActions.remove(action.getActionId());
            }
        }

        /*
         * Destroy the normal value-change transaction immediately.
         *
         * IMPORTANT:
         *
         * Do NOT clear pendingActions here. A failed cross-profile
         * dispatch is intentionally kept pending so the user can retry
         * it after the Work Profile becomes available. Successfully
         * dispatched actions have already been removed above.
         */
        pendingChanges.clear();
        pendingActions.clear();

        /*
         * Authentication is destroyed immediately.
         *
         * It cannot be reused.
         */
        authenticatedSession = false;
    }

    /*
     * ============================================================
     * TEMPORARY ACTION EXECUTION
     * ============================================================
     */

    private boolean applyPendingAction(
            String actionId) {

        if (ACTION_LOCK_PHONE_NOW.equals(actionId)) {

            lockPhoneNow();
            return true;
        }

        /*
         * The maximum Work Profile security action is NOT applied in
         * the parent profile. We send a signed request across the
         * profile boundary; the Work Profile's Profile Owner performs
         * and verifies the actual DevicePolicyManager operations.
         */
        if (DummyActivity.APPLY_MAXIMUM_WORK_PROFILE_SECURITY.equals(actionId)) {
            return requestMaximumWorkProfileSecurity();
        }

        return false;
    }

    /**
     * SECURITY-CRITICAL:
     *
     * DevicePolicyManager.lockNow() is reachable only from
     * applyAuthenticatedChanges() -> applyPendingAction().
     *
     * Therefore the UI cannot execute this operation directly.
     */
    /*
     * SECURITY-CRITICAL:
     *
     * This method only requests the operation in the Work Profile.
     * It MUST NOT call the Work Profile DevicePolicyManager directly
     * from the parent profile.
     *
     * AuthenticationUtility signs the request before it crosses the
     * profile boundary. DummyActivity in the Work Profile verifies that
     * signature and then verifies Profile Owner status again.
     *
     * The parent process does not directly claim that the Work Profile
     * policy has already been applied; the authoritative latch lives in
     * the Work Profile copy of LocalStorageManager.
     */
    private boolean requestMaximumWorkProfileSecurity() {

        Intent intent =
                new Intent(
                        DummyActivity.APPLY_MAXIMUM_WORK_PROFILE_SECURITY
                );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
        );

        try {
            /*
             * SECURITY-CRITICAL: both profile discovery and Intent
             * signing must succeed before the request is considered
             * dispatched. If either step fails, keep the action pending
             * instead of silently consuming it.
             */
            Utility.transferIntentToProfile(
                    context,
                    intent
            );

            context.startActivity(intent);
            return true;

        } catch (RuntimeException e) {
            /*
             * Expected failures include an unavailable Work Profile, no
             * resolvable cross-profile target, or an Activity launch
             * failure. This is a dispatch failure, NOT a policy failure.
             * Do not clear the pending action; the user must be able to
             * retry it after the Work Profile becomes available.
             */
            return false;
        }
    }

    private void lockPhoneNow() {

        DevicePolicyManager dpm =
                (DevicePolicyManager)
                        context.getSystemService(
                                Context.DEVICE_POLICY_SERVICE
                        );

        ComponentName admin =
                new ComponentName(
                        context,
                        ShelterDeviceAdminReceiver.class
                );

        if (dpm == null ||
                !dpm.isAdminActive(admin)) {

            return;
        }

        /*
         * Immediate device lock.
         */
        dpm.lockNow();
    }

    /*
     * ============================================================
     * DEVICE LOCK DELAY
     * ============================================================
     */

    private void applyDeviceLockDelay(int seconds) {

        /*
         * SECURITY POLICY RULE:
         *
         * DevicePolicyManager operation
         * is allowed only after authentication.
         */

        DevicePolicyManager dpm =
                (DevicePolicyManager)
                        context.getSystemService(
                                Context.DEVICE_POLICY_SERVICE
                        );

        ComponentName admin =
                new ComponentName(
                        context,
                        ShelterDeviceAdminReceiver.class
                );

        if (dpm == null ||
                !dpm.isAdminActive(admin)) {

            return;
        }

        /*
         * Always cancel a previously scheduled lock.
         */
        if (pendingLockRunnable != null) {

            lockHandler.removeCallbacks(
                    pendingLockRunnable
            );

            pendingLockRunnable = null;
        }

        /*
         * -1 means:
         *
         * Do nothing / never lock.
         */
        if (seconds == -1) {

            return;
        }

        pendingLockRunnable = () -> {

            if (dpm.isAdminActive(admin)) {

                dpm.lockNow();
            }

            pendingLockRunnable = null;
        };

        lockHandler.postDelayed(
                pendingLockRunnable,
                seconds * 1000L
        );
    }

    /*
     * ============================================================
     * TRANSACTION CLEANUP
     * ============================================================
     */

    /**
     * Clears the current uncommitted transaction.
     *
     * This is used when the Security Policy page is closed
     * without applying its pending changes.
     *
     * IMPORTANT:
     *
     * This does NOT cancel a device-lock timer that was already
     * successfully applied in a previous authenticated transaction.
     */
    public void clearPendingTransaction() {

        pendingChanges.clear();
        pendingActions.clear();

        authenticatedSession = false;
    }

    /**
     * Clears authentication only.
     */
    public void clearAuthenticationSession() {

        /*
         * Authentication cannot survive
         * cancellation or leaving the flow.
         */
        authenticatedSession = false;
    }

    /*
     * ============================================================
     * PENDING ACTION MODEL
     * ============================================================
     */

    private static class PendingSecurityAction {

        private final String actionId;

        PendingSecurityAction(String actionId) {

            this.actionId = actionId;
        }

        String getActionId() {

            return actionId;
        }
    }
}

