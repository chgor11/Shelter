package net.typeblog.shelter.services;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

import net.typeblog.shelter.security.SystemPageFingerprints;
import net.typeblog.shelter.security.SystemPageSecurityGuard;

/**
 * Main-profile AccessibilityService used only to detect the six protected
 * Settings/Samsung Launcher pages.
 *
 * <p>The service is intentionally production-only: no diagnostic tree dumps,
 * no text logging and no polling loop. Android delivers only window-state
 * events for the two relevant packages. The detector then inspects only the
 * exact Accessibility window that generated the event.</p>
 *
 * <p>Resource-id reporting is enabled in accessibility_service_config.xml via
 * flagReportViewIds. Without that flag getViewIdResourceName() may be null and
 * the language-independent fingerprints cannot work reliably.</p>
 */
public final class ShelterAccessibilityService extends AccessibilityService {

    private static final long ROOT_RETRY_DELAY_MS = 80L;

    private SystemPageSecurityGuard mSecurityGuard;
    private Handler mHandler;

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

        // The grace interval is the cheapest possible fast path.
        if (mSecurityGuard.isGraceActive()) {
            return;
        }

        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        final int windowId = event.getWindowId();
        if (windowId < 0) {
            return;
        }

        final CharSequence packageName = event.getPackageName();
        final CharSequence className = event.getClassName();

        // Do not retain the AccessibilityEvent itself: Android owns/recycles it.
        detectWindow(windowId, packageName, className);
    }

    /**
     * Resolves the event's exact window. A single short retry handles the
     * normal transition in which the window exists in the event but its root
     * has not yet been exposed through getWindows().
     */
    private void detectWindow(
            final int windowId,
            final CharSequence packageName,
            final CharSequence className) {

        AccessibilityNodeInfo root = obtainRootForWindow(windowId);
        if (root != null) {
            detectAndRecycle(root, packageName, className);
            return;
        }

        if (mHandler == null) {
            return;
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mSecurityGuard == null || mSecurityGuard.isGraceActive()) {
                    return;
                }

                AccessibilityNodeInfo retryRoot = obtainRootForWindow(windowId);
                if (retryRoot != null) {
                    detectAndRecycle(retryRoot, packageName, className);
                }
            }
        }, ROOT_RETRY_DELAY_MS);
    }

    private void detectAndRecycle(
            AccessibilityNodeInfo root,
            CharSequence packageName,
            CharSequence className) {
        try {
            SystemPageFingerprints.Page page =
                    SystemPageFingerprints.detect(root, packageName, className);

            if (page != SystemPageFingerprints.Page.NONE
                    && mSecurityGuard != null) {
                mSecurityGuard.onProtectedPageDetected(page);
            }
        } finally {
            root.recycle();
        }
    }

    /**
     * Gets the root belonging to the exact Accessibility window id. Falling
     * back to getRootInActiveWindow() is deliberately forbidden because the
     * active window can change between the event and tree inspection.
     */
    private AccessibilityNodeInfo obtainRootForWindow(int targetWindowId) {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            return null;
        }

        for (AccessibilityWindowInfo window : windows) {
            if (window == null) {
                continue;
            }

            try {
                if (window.getId() == targetWindowId) {
                    return window.getRoot();
                }
            } finally {
                window.recycle();
            }
        }

        return null;
    }

    @Override
    public void onInterrupt() {
        // No action required. The security guard fails closed until the
        // AccessibilityService is connected and can inspect a real window.
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

        super.onDestroy();
    }
}
