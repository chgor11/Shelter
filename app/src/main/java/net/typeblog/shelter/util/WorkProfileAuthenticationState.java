package net.typeblog.shelter.util;

import android.os.SystemClock;

/**
 * SECURITY-CRITICAL:
 *
 * Holds the temporary Work Profile authentication lease only in process memory.
 * Nothing in this class is persisted to disk. Therefore a Work Profile
 * Shelter process restart/crash destroys the authentication lease.
 *
 * The actual security decision must use elapsedRealtime(), not wall-clock time.
 */
public final class WorkProfileAuthenticationState {
    public static final long AUTHENTICATION_WINDOW_MS = 5 * 60 * 1000L;
    public static final String EXTRA_AUTH_EXPIRES_AT =
            "net.typeblog.shelter.extra.AUTH_EXPIRES_AT";

    private static volatile long sExpiresAtElapsedRealtime = 0L;

    private WorkProfileAuthenticationState() {
    }

    /**
     * Grants a fresh five-minute authentication lease.
     *
     * @return the monotonic elapsedRealtime() expiration timestamp.
     */
    public static synchronized long grant() {
        long expiresAt = SystemClock.elapsedRealtime() + AUTHENTICATION_WINDOW_MS;
        sExpiresAtElapsedRealtime = expiresAt;
        return expiresAt;
    }

    /**
     * Returns true only while the five-minute lease is still valid.
     * Expiration is checked at the moment of the security decision.
     */
    public static boolean isValid() {
        long expiresAt = sExpiresAtElapsedRealtime;
        if (expiresAt <= 0L) {
            return false;
        }

        if (SystemClock.elapsedRealtime() < expiresAt) {
            return true;
        }

        clear();
        return false;
    }

    public static long getExpiresAtElapsedRealtime() {
        return sExpiresAtElapsedRealtime;
    }

    public static synchronized void clear() {
        sExpiresAtElapsedRealtime = 0L;
    }
}
