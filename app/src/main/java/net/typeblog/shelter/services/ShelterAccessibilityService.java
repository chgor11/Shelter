package net.typeblog.shelter.services;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.util.Log;

import java.util.List;

import net.typeblog.shelter.security.SystemPageFingerprints;
import net.typeblog.shelter.security.SystemPageSecurityGuard;

/**
 * Main-profile AccessibilityService used only as the input channel for the
 * protected system-page security guard.
 *
 * <p>Package filtering is performed by accessibility_service_config.xml first:
 * only Settings and Samsung Launcher events are delivered. The Java detector
 * then applies the exact window class and resource-id fingerprints.</p>
 *
 * <p>Only window-state/window-list changes are requested. High-frequency
 * content-change/scroll/text events are deliberately not subscribed to.</p>
 */
public class ShelterAccessibilityService extends AccessibilityService {
    private static final String TAG = "ShelterAccessibility";

    private SystemPageSecurityGuard mSecurityGuard;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        if (mSecurityGuard == null) {
            mSecurityGuard = new SystemPageSecurityGuard(this);
        }

        Log.i(TAG, "Accessibility security service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || mSecurityGuard == null) {
            return;
        }

        /*
         * The first and cheapest optimization: during the 3-minute grace
         * period there is no reason to inspect an Accessibility tree at all.
         * Multiple window events are therefore almost free.
         */
        if (mSecurityGuard.isGraceActive()) {
            return;
        }

        final int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

        /*
         * XML packageNames is the first filter. The detector repeats the
         * package/class checks as a fail-closed standalone boundary.
         */
        CharSequence packageName = event.getPackageName();
        CharSequence className = event.getClassName();

        /*
         * Never blindly pair an event with getRootInActiveWindow(). During a
         * transition, the active window can already be different from the
         * window that generated this event. We therefore resolve the root from
         * the event's own windowId. If that exact window is unavailable, we
         * fail closed instead of inspecting an unrelated window.
         */
        AccessibilityNodeInfo root = obtainRootForEvent(event);
        if (root == null) {
            return;
        }

        try {
            SystemPageFingerprints.Page page =
                    SystemPageFingerprints.detect(
                            root,
                            packageName,
                            className,
                            event.getText() == null ? null : event.getText().toString()
                    );

            if (page != SystemPageFingerprints.Page.NONE) {
                Log.i(TAG, "Protected system page detected: " + page);
                mSecurityGuard.onProtectedPageDetected(page);
            }
        } finally {
            root.recycle();
        }
    }

    /**
     * Returns the Accessibility root belonging to the exact window that
     * generated {@code event}.
     *
     * <p>A negative window id means that the event does not identify a concrete
     * window. For this security detector we deliberately do not fall back to
     * {@link #getRootInActiveWindow()}, because doing so could combine the
     * identity of one event with the UI tree of another window.</p>
     */
    private AccessibilityNodeInfo obtainRootForEvent(AccessibilityEvent event) {
        final int eventWindowId = event.getWindowId();

        if (eventWindowId < 0) {
            return null;
        }

        final List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            return null;
        }

        for (AccessibilityWindowInfo window : windows) {
            if (window == null) {
                continue;
            }

            try {
                if (window.getId() == eventWindowId) {
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
        Log.w(TAG, "Accessibility security service interrupted");
    }

    @Override
    public void onDestroy() {
        if (mSecurityGuard != null) {
            mSecurityGuard.destroy();
            mSecurityGuard = null;
        }

        super.onDestroy();
    }
}
