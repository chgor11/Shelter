package net.typeblog.shelter.services;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.typeblog.shelter.security.SystemPageFingerprints;
import net.typeblog.shelter.security.SystemPageSecurityGuard;

/**
 * Main-profile AccessibilityService used only to detect the six protected
 * Settings/Samsung Launcher pages.
 *
 * <p>The service deliberately does not assume that the AccessibilityEvent's
 * window is the only window containing the complete page tree. On Samsung
 * devices the event can arrive while the corresponding window is still being
 * populated, and the event window's root can expose only a partial tree.
 * Therefore detection examines each relevant AccessibilityWindowInfo root
 * independently, preferring the event window and then other windows belonging
 * to the same package.</p>
 *
 * <p>Resource-id reporting is enabled in accessibility_service_config.xml via
 * flagReportViewIds. Detection remains strict: the fingerprint itself decides
 * whether a candidate root is a protected page. Resource IDs from different
 * windows are never merged, because doing so could create a false positive.</p>
 */
public final class ShelterAccessibilityService extends AccessibilityService {

    /*
     * A short bounded retry is used because a window may exist before its
     * complete accessibility tree becomes available. No polling loop is used.
     */
    private static final long[] ROOT_RETRY_DELAYS_MS = {0L, 80L, 200L, 500L};

    private SystemPageSecurityGuard mSecurityGuard;
    private Handler mHandler;

    // Last real window-state identity. TYPE_WINDOWS_CHANGED may not carry
    // package/class, so it can reuse the identity of the most recent state
    // event for the same window.
    private String mLastPackageName = "";
    private String mLastClassName = "";
    private int mLastWindowId = -1;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        if (mSecurityGuard == null) {
            mSecurityGuard = new SystemPageSecurityGuard(this);
        }
        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || mSecurityGuard == null) {
            return;
        }

        // Cheapest fast path: do not inspect accessibility trees during grace.
        if (mSecurityGuard.isGraceActive()) {
            return;
        }

        final int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

        int eventWindowId = event.getWindowId();
        String packageName = toStringOrEmpty(event.getPackageName());
        String className = toStringOrEmpty(event.getClassName());

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (eventWindowId < 0 || packageName.length() == 0 || className.length() == 0) {
                return;
            }
            mLastWindowId = eventWindowId;
            mLastPackageName = packageName;
            mLastClassName = className;
        } else {
            if (packageName.length() == 0) {
                packageName = mLastPackageName;
            }
            if (className.length() == 0) {
                className = mLastClassName;
            }
            if (eventWindowId < 0) {
                eventWindowId = mLastWindowId;
            }
            if (packageName.length() == 0 || className.length() == 0 || eventWindowId < 0) {
                return;
            }
        }

        scheduleDetection(packageName, className, eventWindowId, 0);
    }

    private void scheduleDetection(
            final String packageName,
            final String className,
            final int eventWindowId,
            final int retryIndex) {

        if (mHandler == null || mSecurityGuard == null
                || mSecurityGuard.isGraceActive()) {
            return;
        }

        long delay = ROOT_RETRY_DELAYS_MS[retryIndex];

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mSecurityGuard == null || mSecurityGuard.isGraceActive()) {
                    return;
                }

                boolean detected = detectRelevantWindows(
                        packageName, className, eventWindowId);

                if (!detected
                        && retryIndex + 1 < ROOT_RETRY_DELAYS_MS.length
                        && mHandler != null) {
                    scheduleDetection(
                            packageName,
                            className,
                            eventWindowId,
                            retryIndex + 1);
                }
            }
        }, delay);
    }

    /**
     * Examines candidate roots independently.
     *
     * <p>Candidate order:</p>
     * <ol>
     *     <li>the exact window that generated the event;</li>
     *     <li>active/focused windows of the same package;</li>
     *     <li>other windows whose root belongs to the same package.</li>
     * </ol>
     *
     * <p>No IDs or nodes are combined between roots. A positive result must be
     * produced by one complete candidate tree.</p>
     */
    private boolean detectRelevantWindows(
            String packageName,
            String className,
            int eventWindowId) {

        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            return false;
        }

        final List<AccessibilityWindowInfo> ordered = new ArrayList<>();
        final Set<Integer> addedIds = new HashSet<>();

        try {
            // Pass 1: exact event window.
            for (AccessibilityWindowInfo window : windows) {
                if (window == null || window.getId() != eventWindowId) {
                    continue;
                }
                if (addedIds.add(window.getId())) {
                    ordered.add(window);
                }
                break;
            }

            // Pass 2: active/focused windows. The root package is verified
            // before fingerprint detection below.
            for (AccessibilityWindowInfo window : windows) {
                if (window == null || addedIds.contains(window.getId())) {
                    continue;
                }
                if (window.isActive() || window.isFocused()) {
                    if (addedIds.add(window.getId())) {
                        ordered.add(window);
                    }
                }
            }

            // Pass 3: all remaining interactive windows.
            for (AccessibilityWindowInfo window : windows) {
                if (window == null || addedIds.contains(window.getId())) {
                    continue;
                }
                if (addedIds.add(window.getId())) {
                    ordered.add(window);
                }
            }

            for (AccessibilityWindowInfo window : ordered) {
                AccessibilityNodeInfo root = null;
                try {
                    root = window.getRoot();
                    if (root == null) {
                        continue;
                    }

                    /*
                     * Do not inspect an unrelated system window. The event
                     * package/class remain authoritative for page identity,
                     * while the root package confirms that this candidate
                     * belongs to the same application window.
                     */
                    CharSequence rootPackage = root.getPackageName();
                    if (rootPackage == null
                            || !packageName.equals(rootPackage.toString())) {
                        continue;
                    }

                    SystemPageFingerprints.Page page =
                            SystemPageFingerprints.detect(
                                    root, packageName, className);

                    if (page != SystemPageFingerprints.Page.NONE) {
                        if (mSecurityGuard != null) {
                            mSecurityGuard.onProtectedPageDetected(page);
                        }
                        return true;
                    }
                } finally {
                    if (root != null) {
                        root.recycle();
                    }
                }
            }

            return false;
        } finally {
            for (AccessibilityWindowInfo window : windows) {
                if (window != null) {
                    window.recycle();
                }
            }
        }
    }

    private static String toStringOrEmpty(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public void onInterrupt() {
        // No action required.
    }

    @Override
    public void onDestroy() {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }

        if (mSecurityGuard != null) {
            mSecurityGuard.destroy();
            mSecurityGuard = null;
        }

        mLastPackageName = "";
        mLastClassName = "";
        mLastWindowId = -1;

        super.onDestroy();
    }
}
