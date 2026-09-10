package net.typeblog.shelter.services;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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

    /** Diagnostic build: collect evidence without actually locking the device. */
    private static final boolean DIAGNOSTIC_ONLY = true;

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

        final int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

        CharSequence packageName = event.getPackageName();
        CharSequence className = event.getClassName();

        /*
         * DIAGNOSTIC BUILD:
         * Dump every interactive window and every resource-id exposed through
         * Accessibility whenever Settings or Samsung Launcher is involved.
         * This intentionally runs before the grace-period fast path so that
         * the logs remain useful while validating the fingerprints.
         */
        if (isDiagnosticPackage(packageName)) {
            logDiagnosticSnapshot(event);
        }

        if (mSecurityGuard.isGraceActive()) {
            return;
        }

        /*
         * Never blindly pair an event with getRootInActiveWindow(). During a
         * transition, the active window can already be different from the
         * window that generated this event. Resolve the exact event window.
         */
        AccessibilityNodeInfo root = obtainRootForEvent(event);
        if (root == null) {
            Log.d(TAG, "No root for eventWindowId=" + event.getWindowId()
                    + "; package=" + packageName + "; class=" + className);
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

            Log.i(TAG, "Fingerprint result: " + page
                    + "; eventWindowId=" + event.getWindowId()
                    + "; package=" + packageName
                    + "; class=" + className);

            if (page != SystemPageFingerprints.Page.NONE) {
                if (DIAGNOSTIC_ONLY) {
                    Log.w(TAG, "DIAGNOSTIC_ONLY: protected page WOULD trigger lockNow(): " + page);
                } else {
                    mSecurityGuard.onProtectedPageDetected(page);
                }
            }
        } finally {
            root.recycle();
        }
    }

    private static boolean isDiagnosticPackage(CharSequence packageName) {
        if (packageName == null) {
            return false;
        }
        String pkg = packageName.toString();
        return "com.android.settings".equals(pkg)
                || "com.sec.android.app.launcher".equals(pkg);
    }

    /**
     * Diagnostic-only snapshot of all Accessibility interactive windows.
     * Nothing in this method changes enforcement; it only writes Logcat.
     */
    private void logDiagnosticSnapshot(AccessibilityEvent event) {
        final String eventPackage = String.valueOf(event.getPackageName());
        final String eventClass = String.valueOf(event.getClassName());
        final int eventWindowId = event.getWindowId();

        Log.i(TAG, "========== ACCESSIBILITY DIAGNOSTIC BEGIN ==========");
        Log.i(TAG, "EVENT type=" + event.getEventType()
                + ", windowId=" + eventWindowId
                + ", package=" + eventPackage
                + ", class=" + eventClass
                + ", text=" + String.valueOf(event.getText()));

        final List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            Log.w(TAG, "WINDOWS: none returned by getWindows()");
            Log.i(TAG, "========== ACCESSIBILITY DIAGNOSTIC END ==========");
            return;
        }

        Log.i(TAG, "WINDOWS: count=" + windows.size());

        for (AccessibilityWindowInfo window : windows) {
            if (window == null) {
                continue;
            }

            try {
                AccessibilityNodeInfo root = null;
                try {
                    root = window.getRoot();
                    Log.i(TAG, "WINDOW id=" + window.getId()
                            + ", type=" + window.getType()
                            + ", layer=" + window.getLayer()
                            + ", active=" + window.isActive()
                            + ", focused=" + window.isFocused()
                            + ", accessibilityFocused=" + window.isAccessibilityFocused()
                            + ", package=" + String.valueOf(
                                    root == null ? null : root.getPackageName())
                            + ", rootClass=" + String.valueOf(
                                    root == null ? null : root.getClassName())
                            + ", title=" + String.valueOf(window.getTitle())
                            + ", bounds=" + window.getBoundsInScreen());

                    if (root == null) {
                        Log.w(TAG, "WINDOW id=" + window.getId() + " root=null");
                        continue;
                    }

                    Set<String> ids = SystemPageFingerprints.collectResourceIds(root);
                    List<String> sortedIds = new ArrayList<>(ids);
                    Collections.sort(sortedIds);

                    Log.i(TAG, "WINDOW id=" + window.getId()
                            + " RESOURCE_IDS count=" + sortedIds.size());
                    for (String id : sortedIds) {
                        Log.i(TAG, "  RESOURCE_ID " + id);
                    }

                    logFingerprintDiagnostics(window, root, ids);
                } finally {
                    if (root != null) {
                        root.recycle();
                    }
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "WINDOW id=" + window.getId()
                        + " diagnostic failure", e);
            } finally {
                window.recycle();
            }
        }

        Log.i(TAG, "========== ACCESSIBILITY DIAGNOSTIC END ==========");
    }

    private void logFingerprintDiagnostics(
            AccessibilityWindowInfo window,
            AccessibilityNodeInfo root,
            Set<String> ids) {

        final CharSequence windowPackage = root.getPackageName();
        final String pkg = windowPackage == null ? "" : windowPackage.toString();
        final String cls = root.getClassName() == null
                ? ""
                : root.getClassName().toString();

        Log.i(TAG, "WINDOW id=" + window.getId()
                + " ROOT package=" + pkg + ", class=" + cls);

        if ("com.android.settings".equals(pkg)
                && "com.android.settings.SubSettings".equals(cls)) {
            logOneFingerprint(window.getId(),
                    SystemPageFingerprints.Page.DEVELOPER_OPTIONS, ids);
            logOneFingerprint(window.getId(),
                    SystemPageFingerprints.Page.SECURITY_PRIVACY, ids);
            logOneFingerprint(window.getId(),
                    SystemPageFingerprints.Page.DEVICE_ADMIN_APPS, ids);
            logOneFingerprint(window.getId(),
                    SystemPageFingerprints.Page.ACCESSIBILITY_INSTALLED_APPS, ids);
            logOneFingerprint(window.getId(),
                    SystemPageFingerprints.Page.SHELTER_APP_INFO, ids);
        } else if ("com.sec.android.app.launcher".equals(pkg)
                && "com.sec.android.app.launcher.apppicker.AppPickerActivity".equals(cls)) {
            logOneFingerprint(window.getId(),
                    SystemPageFingerprints.Page.HIDDEN_APPS, ids);
        }
    }

    private void logOneFingerprint(
            int windowId,
            SystemPageFingerprints.Page page,
            Set<String> actualIds) {

        Set<String> required = SystemPageFingerprints.requiredFor(page);
        Set<String> forbidden = SystemPageFingerprints.forbiddenFor(page);

        List<String> missing = new ArrayList<>();
        for (String id : required) {
            if (!actualIds.contains(id)) {
                missing.add(id);
            }
        }
        Collections.sort(missing);

        List<String> presentForbidden = new ArrayList<>();
        for (String id : forbidden) {
            if (actualIds.contains(id)) {
                presentForbidden.add(id);
            }
        }
        Collections.sort(presentForbidden);

        Log.i(TAG, "FINGERPRINT page=" + page
                + ", windowId=" + windowId
                + ", required=" + required.size()
                + ", missingRequired=" + missing.size()
                + ", presentForbidden=" + presentForbidden.size());

        for (String id : missing) {
            Log.i(TAG, "  MISSING_REQUIRED page=" + page + " id=" + id);
        }
        for (String id : presentForbidden) {
            Log.i(TAG, "  PRESENT_FORBIDDEN page=" + page + " id=" + id);
        }
    }

    /**
     * Returns the Accessibility root belonging to the exact window that
     * generated {@code event}. A missing/invalid event window fails closed.
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
