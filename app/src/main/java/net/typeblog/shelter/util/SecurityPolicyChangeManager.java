package net.typeblog.shelter.util;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/*
 * SECURITY POLICY ADMINISTRATION RULE
 *
 * THIS CLASS IS THE ONLY AUTHORIZED ENTRY POINT
 * FOR SECURITY POLICY APPLICATION.
 *
 * Every new security policy MUST:
 *
 * 1. Create PendingSecurityChange
 * 2. Wait for user review
 * 3. Require explicit confirmation
 * 4. Require fresh authentication
 * 5. Apply only after authentication success
 *
 * Direct DevicePolicyManager calls outside this class
 * are forbidden.
 */


public class SecurityPolicyChangeManager {


    private static SecurityPolicyChangeManager instance;


    private final Context context;

    private final Handler lockHandler =
            new Handler(Looper.getMainLooper());
    
    private Runnable pendingLockRunnable = null;


    private final Map<String, PendingSecurityChange>
            pendingChanges =
            new HashMap<>();


    /*
     * This flag is created ONLY after
     * successful system authentication.
     *
     * It is valid only for current transaction.
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




    public void addPendingChange(
            String policy,
            String oldValue,
            String newValue) {

        /*
         * IMPORTANT:
         *
         * NEVER APPLY POLICY HERE.
         *
         * Only create pending transaction.
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


        return result.toString();
    }





    /*
     * Called ONLY after successful authentication.
     *
     * This creates a one-time authorization.
     */
    public void authorizeCurrentTransaction() {

        authenticatedSession = true;
    }





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



        pendingChanges.clear();


        /*
         * Authentication is destroyed immediately.
         *
         * It cannot be reused.
         */

        authenticatedSession = false;
    }





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
    
        // Always cancel a previously scheduled lock.
        if (pendingLockRunnable != null) {
            lockHandler.removeCallbacks(
                    pendingLockRunnable
            );
            pendingLockRunnable = null;
        }
    
        // -1 means: do nothing / never lock.
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





    public void clearAuthenticationSession() {


        /*
         * Authentication cannot survive
         * cancellation or leaving the flow.
         */

        authenticatedSession = false;
    }
}
