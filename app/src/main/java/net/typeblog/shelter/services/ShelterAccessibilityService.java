package net.typeblog.shelter.services;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
 * Main-profile AccessibilityService for the six protected system pages.
 *
 * <p>Important Samsung/Android 16 compatibility note: on the target device
 * AccessibilityNodeInfo does not expose the resource IDs present in the
 * UIAutomator captures, even with flagReportViewIds enabled. The live logs
 * show 0-15 resource IDs and therefore the original resource-id-only
 * detector can never match those captures. Detection consequently uses the
 * exact window-state identity observed on the target device: package +
 * activity class + normalized visible page label. Hidden Apps is identified
 * by its unique AppPickerActivity class, and Shelter App Info additionally
 * requires the Shelter label. No generic Settings SubSettings page is locked.
 *
 * <p>The tree is still checked to ensure the event window belongs to the
 * expected package. Detection is event driven and bounded; there is no
 * continuous polling loop.</p>
 */
public final class ShelterAccessibilityService extends AccessibilityService {

    private static final String TAG = "ShelterAccessibility";
    private static final long[] RETRY_DELAYS_MS = {0L, 80L, 200L, 500L};

    private SystemPageSecurityGuard mSecurityGuard;
    private Handler mHandler;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        mSecurityGuard = new SystemPageSecurityGuard(this);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || mSecurityGuard == null || mHandler == null) {
            return;
        }

        if (mSecurityGuard.isGraceActive()) {
            return;
        }

        // Deliberately keep this to window-state changes. TYPE_WINDOWS_CHANGED
        // often has no page identity and could reuse stale information.
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        final String pkg = toStringOrEmpty(event.getPackageName());
        final String cls = toStringOrEmpty(event.getClassName());
        final String visibleText = normalize(eventText(event));
        final int windowId = event.getWindowId();

        if (windowId < 0 || pkg.length() == 0 || cls.length() == 0) {
            return;
        }

        if (!"com.android.settings".equals(pkg)
                && !"com.sec.android.app.launcher".equals(pkg)) {
            return;
        }

        scheduleDetection(pkg, cls, visibleText, windowId, 0);
    }

    private void scheduleDetection(
            final String pkg,
            final String cls,
            final String visibleText,
            final int windowId,
            final int retryIndex) {

        if (mHandler == null || mSecurityGuard == null
                || mSecurityGuard.isGraceActive()) {
            return;
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mSecurityGuard == null || mSecurityGuard.isGraceActive()) {
                    return;
                }

                SystemPageFingerprints.Page page =
                        detectWindow(pkg, cls, visibleText, windowId);

                if (page != SystemPageFingerprints.Page.NONE) {
                    Log.i(TAG, "DETECTED_PAGE=" + page
                            + " package=" + pkg + " class=" + cls);
                    mSecurityGuard.onProtectedPageDetected(page);
                    return;
                }

                if (retryIndex + 1 < RETRY_DELAYS_MS.length) {
                    scheduleDetection(
                            pkg, cls, visibleText, windowId, retryIndex + 1);
                }
            }
        }, RETRY_DELAYS_MS[retryIndex]);
    }

    private SystemPageFingerprints.Page detectWindow(
            String pkg,
            String cls,
            String visibleText,
            int eventWindowId) {

        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            return SystemPageFingerprints.Page.NONE;
        }

        List<AccessibilityWindowInfo> candidates = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();

        try {
            // Exact event window first.
            for (AccessibilityWindowInfo w : windows) {
                if (w != null && w.getId() == eventWindowId && seen.add(w.getId())) {
                    candidates.add(w);
                    break;
                }
            }

            // Then the active/focused window for transient Samsung window-id
            // changes. It must still have the same package at root level.
            for (AccessibilityWindowInfo w : windows) {
                if (w != null && !seen.contains(w.getId())
                        && (w.isActive() || w.isFocused())) {
                    candidates.add(w);
                    seen.add(w.getId());
                }
            }

            for (AccessibilityWindowInfo w : candidates) {
                AccessibilityNodeInfo root = null;
                try {
                    root = w.getRoot();
                    if (root == null) {
                        continue;
                    }

                    CharSequence rootPackage = root.getPackageName();
                    if (rootPackage == null || !pkg.equals(rootPackage.toString())) {
                        continue;
                    }

                    SystemPageFingerprints.Page page =
                            SystemPageFingerprints.detect(
                                    root, pkg, cls, visibleText);
                    if (page != SystemPageFingerprints.Page.NONE) {
                        return page;
                    }
                } finally {
                    if (root != null) {
                        root.recycle();
                    }
                }
            }

            return SystemPageFingerprints.Page.NONE;
        } finally {
            for (AccessibilityWindowInfo w : windows) {
                if (w != null) {
                    w.recycle();
                }
            }
        }
    }

    private static String eventText(AccessibilityEvent event) {
        if (event == null || event.getText() == null || event.getText().isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (CharSequence part : event.getText()) {
            if (part == null) continue;
            if (out.length() > 0) out.append(' ');
            out.append(part);
        }
        return out.toString();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value
                .replace('\u200e', ' ')
                .replace('\u200f', ' ')
                .replace('\u200c', ' ')
                .replace('\u202a', ' ')
                .replace('\u202b', ' ')
                .replace('\u202c', ' ')
                .replace('\u202d', ' ')
                .replace('\u202e', ' ')
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String toStringOrEmpty(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted");
    }

    @Override
    public void onDestroy() {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        if (mSecurityGuard != null) {
            mSecurityGuard.destroy();
            mSecurityGuard = null;
        }
        mHandler = null;
        super.onDestroy();
    }
}
