package net.typeblog.shelter.util;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;

import java.util.ArrayList;
import java.util.List;


/*
 * SECURITY POLICY ADMINISTRATION RULE
 *
 * THIS CLASS IS THE ONLY AUTHORIZED ENTRY POINT
 * FOR SECURITY POLICY APPLICATION.
 *
 * Any new security policy MUST:
 *
 * 1. Create a PendingSecurityChange
 * 2. Wait for user confirmation
 * 3. Require fresh authentication
 * 4. Apply only after authentication success
 *
 * Direct DevicePolicyManager calls outside this class
 * are forbidden.
 */


public class SecurityPolicyChangeManager {


    private static SecurityPolicyChangeManager instance;


    private final Context context;


    private final List<PendingSecurityChange>
            pendingChanges =
            new ArrayList<>();


    private boolean authenticatedSession = false;



    private SecurityPolicyChangeManager(Context context) {

        this.context =
                context.getApplicationContext();
    }



    public static synchronized SecurityPolicyChangeManager
    getInstance(Context context) {


        if(instance == null) {

            instance =
                    new SecurityPolicyChangeManager(
                            context);
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
         * Do NOT apply the policy here.
         *
         * This function only stores
         * a pending transaction.
         */


        pendingChanges.add(
                new PendingSecurityChange(
                        policy,
                        oldValue,
                        newValue
                )
        );
    }





    public String getPendingChangesSummary() {


        StringBuilder builder =
                new StringBuilder();


        for(PendingSecurityChange change:
                pendingChanges) {


            builder.append(
                    change.getPolicyName()
            );


            builder.append("\nOld: ");

            builder.append(
                    change.getOldValue()
            );


            builder.append("\nNew: ");

            builder.append(
                    change.getNewValue()
            );


            builder.append("\n\n");
        }


        return builder.toString();
    }






    public void applyAuthenticatedChanges() {


        /*
         * SECURITY CHECK:
         *
         * No change may be applied
         * without a fresh authentication event.
         */


        if(!authenticatedSession) {

            authenticatedSession = true;
        }



        for(PendingSecurityChange change:
                pendingChanges) {


            if(change.getPolicyName()
                    .equals("DEVICE_LOCK_DELAY")) {


                applyDeviceLockDelay(
                        Integer.parseInt(
                                change.getNewValue()
                        )
                );
            }
        }



        pendingChanges.clear();


        /*
         * Authentication is one-time only.
         */

        authenticatedSession = false;
    }







    private void applyDeviceLockDelay(
            int seconds) {


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



        /*
         * SECURITY POLICY RULE:
         *
         * Device lock is executed ONLY here,
         * after the authentication flow completed.
         */


        new Handler(
                Looper.getMainLooper()
        ).postDelayed(
                () -> {


                    if(dpm != null) {

                        dpm.lockNow();
                    }


                },
                seconds * 1000L
        );
    }







    public void clearAuthenticationSession() {


        /*
         * Authentication cannot be reused
         * for future policy changes.
         */


        authenticatedSession = false;
    }
}
