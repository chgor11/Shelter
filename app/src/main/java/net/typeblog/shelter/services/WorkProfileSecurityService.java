package net.typeblog.shelter.services;

import android.app.admin.DeviceAdminService;
import android.app.admin.DevicePolicyManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;

/**
 * Security service owned by the Work Profile Profile Owner.
 *
 * Android keeps a bound DeviceAdminService for a running Profile Owner and
 * automatically re-binds it after the owner process crashes. This makes the
 * runtime ACTION_SCREEN_OFF receiver recoverable without relying on START_STICKY
 * or on the normal ShelterService/FreezeService lifecycle.
 *
 * Security sequence:
 *   1. On every service creation, take one snapshot of the current lock state.
 *      If the device/profile is already locked, secure it immediately.
 *   2. Register a runtime ACTION_SCREEN_OFF receiver.
 *   3. On every subsequent screen-off event, lock the parent first and then
 *      evict the Work Profile credential-encryption key.
 *
 * This service intentionally does not stop itself.
 */
public class WorkProfileSecurityService extends DeviceAdminService {
    private static final String TAG = "ShelterWorkSecurity";

    private DevicePolicyManager mPolicyManager;
    private ComponentName mAdminComponent;
    private BroadcastReceiver mScreenOffReceiver;

    // Prevents duplicate concurrent execution if startup state checking and a
    // screen-off callback become runnable at nearly the same time.
    private boolean mSecurityOperationRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();

        mPolicyManager = getSystemService(DevicePolicyManager.class);
        mAdminComponent = new ComponentName(
                this,
                ShelterDeviceAdminReceiver.class
        );

        mScreenOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    secureWorkProfile("ACTION_SCREEN_OFF");
                }
            }
        };

        /*
         * ACTION_SCREEN_OFF is intentionally registered at runtime.
         * It must not be added as a manifest implicit broadcast receiver.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                    mScreenOffReceiver,
                    new IntentFilter(Intent.ACTION_SCREEN_OFF),
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(
                    mScreenOffReceiver,
                    new IntentFilter(Intent.ACTION_SCREEN_OFF)
            );
        }

        /*
         * One state snapshot per DeviceAdminService lifetime.
         *
         * This closes the important crash/rebind gap:
         * if Shelter crashed while the phone was unlocked and the phone became
         * locked before Android re-bound DeviceAdminService, ACTION_SCREEN_OFF
         * may already have been missed. We therefore inspect the current state
         * once when the service comes back.
         */
        checkCurrentLockStateOnce();
    }

    private void checkCurrentLockStateOnce() {
        if (mPolicyManager == null ||
                !mPolicyManager.isProfileOwnerApp(getPackageName())) {
            Log.w(TAG, "Startup check skipped: not Profile Owner");
            return;
        }

        KeyguardManager keyguardManager =
                getSystemService(KeyguardManager.class);

        if (keyguardManager == null) {
            Log.w(TAG, "Startup check skipped: KeyguardManager unavailable");
            return;
        }

        boolean locked = keyguardManager.isDeviceLocked();

        Log.i(TAG, "Startup lock-state check: isDeviceLocked=" + locked);

        if (locked) {
            secureWorkProfile("STARTUP_LOCK_STATE");
        }
    }

    private void secureWorkProfile(String reason) {
        if (mSecurityOperationRunning) {
            Log.i(TAG, "Security operation already running; ignoring reason=" + reason);
            return;
        }

        if (mPolicyManager == null ||
                !mPolicyManager.isProfileOwnerApp(getPackageName())) {
            Log.w(TAG, "Security operation skipped: not Profile Owner; reason=" + reason);
            return;
        }

        mSecurityOperationRunning = true;

        try {
            /*
             * getParentProfileInstance() must be obtained from the Work Profile
             * DPM. The operation is deliberately performed in this order:
             *
             *   1. lock Parent
             *   2. evict Work Profile credential-encryption key
             *
             * This is the ordering required for the managed-profile EVICT path.
             */
            DevicePolicyManager parentDpm =
                    mPolicyManager.getParentProfileInstance(mAdminComponent);

            Log.i(TAG, "Locking parent first; reason=" + reason);
            parentDpm.lockNow();

            /*
             * FLAG_EVICT_CREDENTIAL_ENCRYPTION_KEY is intended for a managed
             * profile whose per-user credential-encrypted storage is active.
             */
            int encryptionStatus =
                    mPolicyManager.getStorageEncryptionStatus();

            if (encryptionStatus != DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER) {
                Log.w(
                        TAG,
                        "Work Profile key eviction skipped: encryption status="
                                + encryptionStatus
                                + "; reason=" + reason
                );
                return;
            }

            Log.i(TAG, "Evicting Work Profile credential key; reason=" + reason);

            mPolicyManager.lockNow(
                    DevicePolicyManager.FLAG_EVICT_CREDENTIAL_ENCRYPTION_KEY
            );

            Log.i(TAG, "Work Profile credential key eviction requested successfully");
        } catch (SecurityException e) {
            Log.e(TAG, "Work Profile security operation rejected; reason=" + reason, e);
        } catch (RuntimeException e) {
            Log.e(TAG, "Work Profile security operation failed; reason=" + reason, e);
        } finally {
            mSecurityOperationRunning = false;
        }
    }

    @Override
    public void onDestroy() {
        if (mScreenOffReceiver != null) {
            try {
                unregisterReceiver(mScreenOffReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver was not registered.
            }
            mScreenOffReceiver = null;
        }

        super.onDestroy();
    }
}
