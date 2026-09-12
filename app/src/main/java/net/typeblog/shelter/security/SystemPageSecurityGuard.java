package net.typeblog.shelter.security;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;

import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;

/**
 * Main-profile enforcement gate for the six protected system pages.
 *
 * <p>The AccessibilityService reports only a page fingerprint. This class owns
 * the enforcement state and therefore keeps page detection separate from the
 * security action.</p>
 *
 * <p>The grace period is measured exclusively with
 * {@link SystemClock#elapsedRealtime()}, which is monotonic and is not changed
 * when the user changes the wall clock/time zone.</p>
 *
 * <p>No periodic ticking is required. The deadline is checked when another
 * protected-page report arrives. A Handler is used only to clear stale
 * in-process state after the deadline; it is never the authority for expiry.</p>
 *
 * <p>This guard must run in Shelter's main/personal profile. The Work Profile
 * copy is explicitly rejected.</p>
 */
public final class SystemPageSecurityGuard {
    private static final String TAG = "ShelterPageSecurity";
    private static final long GRACE_PERIOD_MS = 1L * 20L * 1000L;

    private final Context mContext;
    private final DevicePolicyManager mDpm;
    private final ComponentName mAdmin;
    private final Handler mHandler;

    /*
     * Access is serialized because AccessibilityService callbacks are normally
     * delivered on its main thread, but keeping the state synchronized also
     * makes the guard safe if it is called from another thread later.
     */
    private long mGraceUntilElapsed = 0L;
    private Runnable mExpiryRunnable;

    public SystemPageSecurityGuard(Context context) {
        mContext = context.getApplicationContext();
        mDpm = mContext.getSystemService(DevicePolicyManager.class);
        mAdmin = new ComponentName(
                mContext,
                ShelterDeviceAdminReceiver.class
        );
        mHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Fast path used by AccessibilityService before it asks for the expensive
     * Accessibility tree. While grace is active, all page reports are ignored.
     */
    public synchronized boolean isGraceActive() {
        return remainingGraceMsLocked() > 0L;
    }

    /**
     * Called only after SystemPageFingerprints has positively identified a
     * protected page.
     */
    public synchronized void onProtectedPageDetected(
            SystemPageFingerprints.Page page) {

        if (page == null || page == SystemPageFingerprints.Page.NONE) {
            return;
        }

        final long now = SystemClock.elapsedRealtime();

        if (mGraceUntilElapsed > now) {
            Log.d(TAG, "DIAG_GUARD_DECISION=GRACE_ACTIVE page=" + page
                    + " remainingMs=" + (mGraceUntilElapsed - now));
            // This is exactly the requested 3-minute grace behavior:
            // repeated Accessibility events/pages do not cause another lock.
            return;
        }

        boolean adminActive = isMainProfileLockedToThisAdmin();
        Log.i(TAG, "DIAG_GUARD_PRELOCK page=" + page
                + " adminActive=" + adminActive
                + " managedProfile=" + isManagedProfile());

        if (!adminActive) {
            Log.w(TAG,
                    "Ignoring protected-page report because main-profile "
                            + "Device Admin is not active: " + page);
            return;
        }

        /*
         * Capture the monotonic timestamp immediately before lockNow(). If the
         * call returns normally, this becomes the start of the grace interval.
         * We do not use wall-clock time anywhere in this state machine.
         */
        final long lockStart = SystemClock.elapsedRealtime();

        try {
            Log.i(TAG, "DIAG_LOCKNOW_ENTER page=" + page);
            Log.i(TAG, "Protected page detected: " + page
                    + "; executing main-profile lockNow()");
            mDpm.lockNow();
            Log.i(TAG, "DIAG_LOCKNOW_RETURNED_NORMALLY page=" + page);
        } catch (SecurityException e) {
            /*
             * Fail closed: if lockNow() is rejected, do NOT open a 3-minute
             * grace window, because doing so would turn a failed lock into an
             * authentication bypass.
             */
            Log.e(TAG,
                    "lockNow() rejected; grace period NOT started; page=" + page,
                    e);
            return;
        } catch (RuntimeException e) {
            Log.e(TAG,
                    "lockNow() failed; grace period NOT started; page=" + page,
                    e);
            return;
        }

        mGraceUntilElapsed = lockStart + GRACE_PERIOD_MS;
        scheduleExpiryLocked(mGraceUntilElapsed);

        Log.i(TAG,
                "3-minute monotonic grace period started for " + page
                        + "; remainingMs=" + GRACE_PERIOD_MS);
    }

    private boolean isManagedProfile() {
        UserManager userManager = mContext.getSystemService(UserManager.class);
        return userManager != null && userManager.isManagedProfile();
    }

    private boolean isMainProfileLockedToThisAdmin() {
        if (mDpm == null) {
            Log.w(TAG, "DIAG_ADMIN_CHECK dpm=NULL");
            return false;
        }

        if (isManagedProfile()) {
            Log.w(TAG, "DIAG_ADMIN_CHECK managedProfile=true");
            return false;
        }

        boolean active = mDpm.isAdminActive(mAdmin);
        Log.i(TAG, "DIAG_ADMIN_CHECK managedProfile=false adminActive=" + active
                + " admin=" + mAdmin.flattenToShortString());
        return active;
    }

    private long remainingGraceMsLocked() {
        final long deadline = mGraceUntilElapsed;
        if (deadline <= 0L) {
            return 0L;
        }

        final long remaining = deadline - SystemClock.elapsedRealtime();

        if (remaining <= 0L) {
            mGraceUntilElapsed = 0L;
            cancelExpiryLocked();
            return 0L;
        }

        return remaining;
    }

    private void scheduleExpiryLocked(final long deadlineElapsed) {
        cancelExpiryLocked();

        long delay = deadlineElapsed - SystemClock.elapsedRealtime();
        if (delay <= 0L) {
            mGraceUntilElapsed = 0L;
            return;
        }

        mExpiryRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (SystemPageSecurityGuard.this) {
                    if (mGraceUntilElapsed == deadlineElapsed
                            && SystemClock.elapsedRealtime() >= deadlineElapsed) {
                        mGraceUntilElapsed = 0L;
                        mExpiryRunnable = null;
                        Log.i(TAG, "3-minute grace period expired");
                    }
                }
            }
        };

        mHandler.postDelayed(mExpiryRunnable, delay);
    }

    private void cancelExpiryLocked() {
        if (mExpiryRunnable != null) {
            mHandler.removeCallbacks(mExpiryRunnable);
            mExpiryRunnable = null;
        }
    }

    public synchronized void destroy() {
        cancelExpiryLocked();
        mGraceUntilElapsed = 0L;
    }
}
