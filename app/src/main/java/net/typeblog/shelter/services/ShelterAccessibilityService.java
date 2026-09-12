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
        Log.i(TAG, "DIAG_SERVICE_CONNECTED package=" + getPackageName());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || mSecurityGuard == null || mHandler == null) {
            return;
        }

        if (mSecurityGuard.isGraceActive()) {
            Log.d(TAG, "DIAG_EVENT_IGNORED reason=GRACE_ACTIVE");
            return;
        }

        // Deliberately keep this to window-state changes. TYPE_WINDOWS_CHANGED
        // often has no page identity and could reuse stale information.
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "DIAG_EVENT_IGNORED reason=EVENT_TYPE type=" + event.getEventType());
            return;
        }

        final String pkg = toStringOrEmpty(event.getPackageName());
        final String cls = toStringOrEmpty(event.getClassName());
        final String visibleText = normalize(eventText(event));
        final int windowId = event.getWindowId();

        Log.i(TAG, "DIAG_EVENT_RECEIVED type=WINDOW_STATE_CHANGED"
                + " package=" + pkg
                + " class=" + cls
                + " windowId=" + windowId
                + " text=[" + visibleText + "]");

        if (windowId < 0 || pkg.length() == 0 || cls.length() == 0) {
            Log.w(TAG, "DIAG_EVENT_REJECTED reason=MISSING_EVENT_ID_PACKAGE_OR_CLASS");
            return;
        }

        if (!"com.android.settings".equals(pkg)
                && !"com.sec.android.app.launcher".equals(pkg)) {
            Log.d(TAG, "DIAG_EVENT_REJECTED reason=PACKAGE_NOT_TARGET package=" + pkg);
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

                Log.d(TAG, "DIAG_DETECT_ATTEMPT retry=" + retryIndex
                        + " package=" + pkg
                        + " class=" + cls
                        + " eventWindowId=" + windowId
                        + " text=[" + visibleText + "]");

                SystemPageFingerprints.Page page =
                        detectWindow(pkg, cls, visibleText, windowId);

                Log.i(TAG, "DIAG_FINGERPRINT_RESULT retry=" + retryIndex
                        + " result=" + page);

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
            Log.w(TAG, "DIAG_WINDOWS count=0");
            return SystemPageFingerprints.Page.NONE;
        }
        Log.d(TAG, "DIAG_WINDOWS count=" + windows.size());

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
                        Log.w(TAG, "DIAG_WINDOW id=" + w.getId() + " root=NULL active=" + w.isActive() + " focused=" + w.isFocused());
                        continue;
                    }

                    CharSequence rootPackage = root.getPackageName();
                    String rootPkg = rootPackage == null ? "" : rootPackage.toString();
                    Log.d(TAG, "DIAG_WINDOW id=" + w.getId()
                            + " type=" + w.getType()
                            + " active=" + w.isActive()
                            + " focused=" + w.isFocused()
                            + " rootPackage=" + rootPkg
                            + " rootClass=" + safeNodeClass(root));

                    if (!pkg.equals(rootPkg)) {
                        Log.d(TAG, "DIAG_WINDOW_REJECTED id=" + w.getId() + " reason=ROOT_PACKAGE_MISMATCH");
                        continue;
                    }

                    logTreeSummary(root);

                    SystemPageFingerprints.Page page =
                            SystemPageFingerprints.detect(
                                    root, pkg, cls, visibleText);
                    Log.d(TAG, "DIAG_WINDOW_FINGERPRINT id=" + w.getId() + " result=" + page);
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

    private static String safeNodeClass(AccessibilityNodeInfo node) {
        if (node == null || node.getClassName() == null) return "";
        return node.getClassName().toString();
    }

    /** Diagnostic-only tree summary. It does not influence detection or security decisions. */
    private void logTreeSummary(AccessibilityNodeInfo root) {
        final int maxNodes = 500;
        ArrayList<AccessibilityNodeInfo> stack = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int visited = 0;
        stack.add(AccessibilityNodeInfo.obtain(root));
        try {
            while (!stack.isEmpty() && visited < maxNodes) {
                AccessibilityNodeInfo node = stack.remove(stack.size() - 1);
                if (node == null) continue;
                visited++;
                try {
                    String id = node.getViewIdResourceName();
                    if (id != null && !id.isEmpty()) ids.add(id);
                    for (int i = 0; i < node.getChildCount(); i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) stack.add(child);
                    }
                } finally {
                    node.recycle();
                }
            }
        } finally {
            for (AccessibilityNodeInfo node : stack) {
                if (node != null) node.recycle();
            }
        }
        Log.i(TAG, "DIAG_TREE_SUMMARY visited=" + visited
                + " resourceIdCount=" + ids.size()
                + " resourceIds=" + ids);
        if (visited >= maxNodes) {
            Log.w(TAG, "DIAG_TREE_TRUNCATED maxNodes=" + maxNodes);
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
