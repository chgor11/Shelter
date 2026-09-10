package net.typeblog.shelter.services;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import net.typeblog.shelter.security.SystemPageFingerprints;
import net.typeblog.shelter.security.SystemPageSecurityGuard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Main-profile AccessibilityService used as the input channel for the
 * protected system-page security guard.
 *
 * <p>Package filtering is performed by accessibility_service_config.xml.
 * The Java detector then applies the configured page fingerprints.</p>
 *
 * <p>Only window-state/window-list changes are requested. High-frequency
 * content-change/scroll/text events are deliberately not subscribed to.</p>
 *
 * <p>
 * DIAGNOSTIC BUILD:
 * This version contains an extended AccessibilityNodeInfo tree dumper.
 * It is intended to discover exactly what Android exposes to the
 * AccessibilityService for each protected page.
 *
 * IMPORTANT:
 * DIAGNOSTIC_ONLY must remain true while collecting diagnostic logs.
 * In this mode no actual lockNow() enforcement is performed by this class.
 * </p>
 */
public class ShelterAccessibilityService extends AccessibilityService {

    private static final String TAG = "ShelterAccessibility";

    /**
     * Diagnostic build:
     * true  = collect evidence only; NEVER perform the security lock.
     * false = allow the normal SystemPageSecurityGuard enforcement path.
     *
     * Keep this TRUE while investigating Accessibility fingerprints.
     */
    private static final boolean DIAGNOSTIC_ONLY = true;

    /**
     * Safety limit for recursive AccessibilityNodeInfo logging.
     *
     * A complex Settings page can expose a very large accessibility tree.
     * This prevents a single window from flooding Logcat indefinitely.
     */
    private static final int MAX_DIAGNOSTIC_NODES = 2000;

    private SystemPageSecurityGuard mSecurityGuard;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        if (mSecurityGuard == null) {
            mSecurityGuard = new SystemPageSecurityGuard(this);
        }

        Log.i(TAG, "Accessibility security service connected");
        Log.i(TAG, "DIAGNOSTIC_ONLY=" + DIAGNOSTIC_ONLY);
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

        final CharSequence packageName = event.getPackageName();
        final CharSequence className = event.getClassName();

        /*
         * DIAGNOSTIC BUILD
         *
         * Dump the complete accessibility window/node information before
         * applying the normal grace-period fast path.
         *
         * This is intentional: while investigating fingerprints we want
         * diagnostic information even when the security grace period is
         * currently active.
         */
        if (isDiagnosticPackage(packageName)) {
            logDiagnosticSnapshot(event);
        }

        /*
         * If the security guard is already inside its grace period, no new
         * detection is necessary.
         */
        if (mSecurityGuard.isGraceActive()) {
            return;
        }

        /*
         * Never blindly pair an event with getRootInActiveWindow().
         *
         * During transitions, the currently active window can already be
         * different from the window which generated this event.
         *
         * Therefore resolve the exact AccessibilityWindowInfo using the
         * event's window ID.
         */
        AccessibilityNodeInfo root = obtainRootForEvent(event);

        if (root == null) {
            Log.w(TAG,
                    "No root for eventWindowId=" + event.getWindowId()
                            + "; package=" + packageName
                            + "; class=" + className);
            return;
        }

        try {
            SystemPageFingerprints.Page page =
                    SystemPageFingerprints.detect(
                            root,
                            packageName,
                            className,
                            event.getText() == null
                                    ? null
                                    : event.getText().toString()
                    );

            Log.i(TAG,
                    "Fingerprint result: " + page
                            + "; eventWindowId=" + event.getWindowId()
                            + "; package=" + packageName
                            + "; class=" + className);

            if (page != SystemPageFingerprints.Page.NONE) {

                if (DIAGNOSTIC_ONLY) {

                    /*
                     * Diagnostic mode deliberately does NOT call lockNow().
                     */
                    Log.w(TAG,
                            "DIAGNOSTIC_ONLY: protected page WOULD trigger "
                                    + "lockNow(): " + page);

                } else {

                    /*
                     * Normal enforcement path.
                     */
                    mSecurityGuard.onProtectedPageDetected(page);
                }
            }

        } finally {
            root.recycle();
        }
    }

    /**
     * Returns true only for packages relevant to the protected-page
     * diagnostic/fingerprint system.
     */
    private static boolean isDiagnosticPackage(CharSequence packageName) {
        if (packageName == null) {
            return false;
        }

        final String pkg = packageName.toString();

        return "com.android.settings".equals(pkg)
                || "com.sec.android.app.launcher".equals(pkg);
    }

    /**
     * Extended Diagnostic 2 snapshot.
     *
     * <p>
     * This method intentionally examines every AccessibilityWindowInfo
     * currently exposed by getWindows(), not merely the event window.
     * </p>
     *
     * <p>
     * For every window it dumps:
     * - window metadata
     * - root metadata
     * - complete AccessibilityNodeInfo tree
     * - resource IDs
     * - text/content descriptions
     * - class/package
     * - bounds
     * - interactive/state properties
     * - current fingerprint diagnostics
     * </p>
     */
    private void logDiagnosticSnapshot(AccessibilityEvent event) {

        final String eventPackage =
                String.valueOf(event.getPackageName());

        final String eventClass =
                String.valueOf(event.getClassName());

        final int eventWindowId =
                event.getWindowId();

        Log.i(TAG,
                "==================================================");

        Log.i(TAG,
                "========== ACCESSIBILITY DIAGNOSTIC 2 BEGIN ==========");

        Log.i(TAG,
                "EVENT"
                        + " type=" + event.getEventType()
                        + " typeName="
                        + AccessibilityEvent.eventTypeToString(
                                event.getEventType())
                        + " windowId=" + eventWindowId
                        + " package=" + eventPackage
                        + " class=" + eventClass
                        + " text=" + String.valueOf(event.getText())
                        + " contentDescription="
                        + String.valueOf(event.getContentDescription()));

        final List<AccessibilityWindowInfo> windows = getWindows();

        if (windows == null || windows.isEmpty()) {

            Log.w(TAG,
                    "WINDOWS: none returned by getWindows()");

            Log.i(TAG,
                    "========== ACCESSIBILITY DIAGNOSTIC 2 END ==========");

            return;
        }

        Log.i(TAG,
                "WINDOWS count=" + windows.size());

        int totalNodes = 0;

        /*
         * Iterate over every interactive AccessibilityWindowInfo.
         */
        for (AccessibilityWindowInfo window : windows) {

            if (window == null) {
                continue;
            }

            AccessibilityNodeInfo root = null;

            try {

                /*
                 * Obtain the root belonging to THIS exact window.
                 */
                root = window.getRoot();

                Rect windowBounds = new Rect();

                try {
                    window.getBoundsInScreen(windowBounds);
                } catch (Exception e) {
                    Log.w(TAG,
                            "Unable to obtain window bounds"
                                    + " windowId=" + window.getId()
                                    + " error="
                                    + e.getClass().getSimpleName()
                                    + ":"
                                    + e.getMessage());
                }

                Log.i(TAG,
                        "--------------------------------------------------");

                Log.i(TAG,
                        "WINDOW"
                                + " id=" + window.getId()
                                + " eventWindow="
                                + (window.getId() == eventWindowId)
                                + " type=" + window.getType()
                                + " layer=" + window.getLayer()
                                + " active=" + window.isActive()
                                + " focused=" + window.isFocused()
                                + " accessibilityFocused="
                                + window.isAccessibilityFocused()
                                + " title="
                                + String.valueOf(window.getTitle())
                                + " bounds=" + windowBounds);

                /*
                 * If Android exposes no root for this window, record that
                 * fact explicitly and continue with the remaining windows.
                 */
                if (root == null) {

                    Log.w(TAG,
                            "WINDOW_ROOT=NULL"
                                    + " id=" + window.getId());

                    continue;
                }

                /*
                 * Root-level information.
                 */
                Log.i(TAG,
                        "WINDOW_ROOT"
                                + " id=" + window.getId()
                                + " package="
                                + String.valueOf(root.getPackageName())
                                + " class="
                                + String.valueOf(root.getClassName())
                                + " text="
                                + String.valueOf(root.getText())
                                + " contentDescription="
                                + String.valueOf(
                                        root.getContentDescription())
                                + " viewId="
                                + String.valueOf(
                                        root.getViewIdResourceName())
                                + " hintText="
                                + String.valueOf(root.getHintText())
                                + " paneTitle="
                                + String.valueOf(root.getPaneTitle())
                                + " childCount="
                                + root.getChildCount());

                /*
                 * Dump the complete node tree.
                 */
                int[] nodeCount = new int[]{0};

                dumpAccessibilityNodeTree(
                        root,
                        0,
                        nodeCount,
                        eventWindowId,
                        window.getId()
                );

                totalNodes += nodeCount[0];

                Log.i(TAG,
                        "WINDOW_SUMMARY"
                                + " id=" + window.getId()
                                + " nodesDumped="
                                + nodeCount[0]);

                /*
                 * Keep the old resource-ID diagnostics as well.
                 *
                 * This allows us to directly compare:
                 *
                 * 1. complete tree
                 * 2. collected resource IDs
                 * 3. fingerprint required/forbidden IDs
                 */
                Set<String> ids =
                        SystemPageFingerprints.collectResourceIds(root);

                List<String> sortedIds =
                        new ArrayList<>(ids);

                Collections.sort(sortedIds);

                Log.i(TAG,
                        "WINDOW_RESOURCE_IDS"
                                + " id=" + window.getId()
                                + " count="
                                + sortedIds.size());

                for (String id : sortedIds) {
                    Log.i(TAG,
                            "  RESOURCE_ID " + id);
                }

                /*
                 * Compare this actual window against the configured
                 * fingerprints.
                 */
                logFingerprintDiagnostics(
                        window,
                        root,
                        ids
                );

            } catch (Exception e) {

                /*
                 * Accessibility trees can change while being traversed.
                 * Do not let one invalid/stale node/window kill diagnostics.
                 */
                Log.e(TAG,
                        "WINDOW_DIAGNOSTIC_ERROR"
                                + " id=" + window.getId()
                                + " error="
                                + e.getClass().getSimpleName()
                                + ":"
                                + e.getMessage(),
                        e);

            } finally {

                if (root != null) {
                    root.recycle();
                }

                /*
                 * getWindows() returns AccessibilityWindowInfo objects which
                 * should be recycled after use.
                 */
                window.recycle();
            }
        }

        Log.i(TAG,
                "TOTAL_NODES_DUMPED=" + totalNodes);

        Log.i(TAG,
                "========== ACCESSIBILITY DIAGNOSTIC 2 END ==========");

        Log.i(TAG,
                "==================================================");
    }

    /**
     * Recursively dumps an AccessibilityNodeInfo tree.
     *
     * Every node is logged with the attributes that are useful for designing
     * a robust page fingerprint.
     */
    private void dumpAccessibilityNodeTree(
            AccessibilityNodeInfo node,
            int depth,
            int[] count,
            int eventWindowId,
            int currentWindowId) {

        if (node == null) {
            return;
        }

        /*
         * Hard safety limit.
         */
        if (count[0] >= MAX_DIAGNOSTIC_NODES) {
            return;
        }

        count[0]++;

        /*
         * Visual indentation makes the hierarchy readable in Logcat.
         */
        StringBuilder prefix = new StringBuilder();

        for (int i = 0; i < depth; i++) {
            prefix.append("  ");
        }

        Rect bounds = new Rect();

        try {
            node.getBoundsInScreen(bounds);
        } catch (Exception e) {
            Log.w(TAG,
                    prefix
                            + "NODE_BOUNDS_ERROR"
                            + " error="
                            + e.getClass().getSimpleName()
                            + ":"
                            + e.getMessage());
        }

        /*
         * Build one detailed line per node.
         */
        StringBuilder line = new StringBuilder();

        line.append(prefix)
                .append("NODE")
                .append(" #")
                .append(count[0])
                .append(" depth=")
                .append(depth)

                .append(" windowId=")
                .append(currentWindowId)

                .append(" eventWindow=")
                .append(currentWindowId == eventWindowId)

                .append(" class=")
                .append(String.valueOf(node.getClassName()))

                .append(" package=")
                .append(String.valueOf(node.getPackageName()))

                .append(" text=")
                .append(String.valueOf(node.getText()))

                .append(" contentDescription=")
                .append(String.valueOf(
                        node.getContentDescription()))

                .append(" viewId=")
                .append(String.valueOf(
                        node.getViewIdResourceName()))

                .append(" hintText=")
                .append(String.valueOf(
                        node.getHintText()))

                .append(" paneTitle=")
                .append(String.valueOf(
                        node.getPaneTitle()))

                .append(" bounds=")
                .append(bounds)

                .append(" clickable=")
                .append(node.isClickable())

                .append(" enabled=")
                .append(node.isEnabled())

                .append(" focusable=")
                .append(node.isFocusable())

                .append(" focused=")
                .append(node.isFocused())

                .append(" accessibilityFocused=")
                .append(node.isAccessibilityFocused())

                .append(" scrollable=")
                .append(node.isScrollable())

                .append(" checkable=")
                .append(node.isCheckable())

                .append(" checked=")
                .append(node.isChecked())

                .append(" selected=")
                .append(node.isSelected())

                .append(" longClickable=")
                .append(node.isLongClickable())

                .append(" visibleToUser=")
                .append(node.isVisibleToUser())

                .append(" childCount=")
                .append(node.getChildCount());

        Log.i(TAG, line.toString());

        final int childCount = node.getChildCount();

        /*
         * Recursively traverse children.
         */
        for (int i = 0; i < childCount; i++) {

            if (count[0] >= MAX_DIAGNOSTIC_NODES) {

                Log.w(TAG,
                        "NODE_LIMIT_REACHED="
                                + MAX_DIAGNOSTIC_NODES
                                + " windowId="
                                + currentWindowId);

                break;
            }

            AccessibilityNodeInfo child = null;

            try {

                child = node.getChild(i);

                if (child != null) {

                    dumpAccessibilityNodeTree(
                            child,
                            depth + 1,
                            count,
                            eventWindowId,
                            currentWindowId
                    );
                }

            } catch (Exception e) {

                /*
                 * A node can become invalid while Android is updating the
                 * accessibility tree. Record the failure and continue.
                 */
                Log.w(TAG,
                        prefix
                                + "CHILD_ERROR"
                                + " index=" + i
                                + " windowId=" + currentWindowId
                                + " error="
                                + e.getClass().getSimpleName()
                                + ":"
                                + e.getMessage());

            } finally {

                if (child != null) {
                    child.recycle();
                }
            }
        }
    }

    /**
     * Compares the actual resource IDs exposed by a window against the
     * currently configured fingerprints.
     *
     * <p>
     * This is diagnostic only. It does not alter the fingerprint definitions.
     * </p>
     */
    private void logFingerprintDiagnostics(
            AccessibilityWindowInfo window,
            AccessibilityNodeInfo root,
            Set<String> ids) {

        final CharSequence windowPackage =
                root.getPackageName();

        final String pkg =
                windowPackage == null
                        ? ""
                        : windowPackage.toString();

        final String cls =
                root.getClassName() == null
                        ? ""
                        : root.getClassName().toString();

        Log.i(TAG,
                "FINGERPRINT_WINDOW"
                        + " id=" + window.getId()
                        + " package=" + pkg
                        + " class=" + cls);

        /*
         * Android Settings pages.
         */
        if ("com.android.settings".equals(pkg)
                && "com.android.settings.SubSettings".equals(cls)) {

            logOneFingerprint(
                    window.getId(),
                    SystemPageFingerprints.Page.DEVELOPER_OPTIONS,
                    ids);

            logOneFingerprint(
                    window.getId(),
                    SystemPageFingerprints.Page.SECURITY_PRIVACY,
                    ids);

            logOneFingerprint(
                    window.getId(),
                    SystemPageFingerprints.Page.DEVICE_ADMIN_APPS,
                    ids);

            logOneFingerprint(
                    window.getId(),
                    SystemPageFingerprints.Page.ACCESSIBILITY_INSTALLED_APPS,
                    ids);

            logOneFingerprint(
                    window.getId(),
                    SystemPageFingerprints.Page.SHELTER_APP_INFO,
                    ids);

        } else if ("com.android.settings".equals(pkg)
                && "com.android.settings.applications.InstalledAppDetailsTop"
                .equals(cls)) {

            /*
             * This is the actual class observed previously for Shelter App
             * Info on the user's Android 16 / One UI device.
             *
             * We intentionally log the Shelter App Info fingerprint here
             * without changing the actual detector yet.
             */
            Log.i(TAG,
                    "APP_INFO_CLASS_DETECTED"
                            + " package=" + pkg
                            + " class=" + cls);

            logOneFingerprint(
                    window.getId(),
                    SystemPageFingerprints.Page.SHELTER_APP_INFO,
                    ids);

        } else if ("com.sec.android.app.launcher".equals(pkg)
                && "com.sec.android.app.launcher.apppicker.AppPickerActivity"
                .equals(cls)) {

            /*
             * Samsung Launcher -> Hidden apps.
             */
            logOneFingerprint(
                    window.getId(),
                    SystemPageFingerprints.Page.HIDDEN_APPS,
                    ids);
        }
    }

    /**
     * Logs the exact required/forbidden resource IDs for one fingerprint.
     *
     * <p>
     * This is particularly useful when resource IDs are unexpectedly absent
     * from the Accessibility tree.
     * </p>
     */
    private void logOneFingerprint(
            int windowId,
            SystemPageFingerprints.Page page,
            Set<String> actualIds) {

        Set<String> required =
                SystemPageFingerprints.requiredFor(page);

        Set<String> forbidden =
                SystemPageFingerprints.forbiddenFor(page);

        List<String> missing =
                new ArrayList<>();

        for (String id : required) {

            if (!actualIds.contains(id)) {
                missing.add(id);
            }
        }

        Collections.sort(missing);

        List<String> presentForbidden =
                new ArrayList<>();

        for (String id : forbidden) {

            if (actualIds.contains(id)) {
                presentForbidden.add(id);
            }
        }

        Collections.sort(presentForbidden);

        Log.i(TAG,
                "FINGERPRINT"
                        + " page=" + page
                        + " windowId=" + windowId
                        + " required=" + required.size()
                        + " actualIds=" + actualIds.size()
                        + " missingRequired=" + missing.size()
                        + " presentForbidden="
                        + presentForbidden.size());

        for (String id : missing) {

            Log.i(TAG,
                    "  MISSING_REQUIRED"
                            + " page=" + page
                            + " id=" + id);
        }

        for (String id : presentForbidden) {

            Log.i(TAG,
                    "  PRESENT_FORBIDDEN"
                            + " page=" + page
                            + " id=" + id);
        }
    }

    /**
     * Returns the Accessibility root belonging to the exact window that
     * generated the event.
     *
     * <p>
     * A missing/invalid event window fails closed.
     * </p>
     */
    private AccessibilityNodeInfo obtainRootForEvent(
            AccessibilityEvent event) {

        final int eventWindowId =
                event.getWindowId();

        if (eventWindowId < 0) {
            return null;
        }

        final List<AccessibilityWindowInfo> windows =
                getWindows();

        if (windows == null || windows.isEmpty()) {
            return null;
        }

        for (AccessibilityWindowInfo window : windows) {

            if (window == null) {
                continue;
            }

            try {

                if (window.getId() == eventWindowId) {

                    /*
                     * The WindowInfo is recycled in finally below.
                     *
                     * The returned AccessibilityNodeInfo is independent
                     * and is recycled by the caller.
                     */
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
        Log.w(TAG,
                "Accessibility security service interrupted");
    }

    @Override
    public void onDestroy() {

        if (mSecurityGuard != null) {

            mSecurityGuard.destroy();
            mSecurityGuard = null;
        }

        Log.i(TAG,
                "Accessibility security service destroyed");

        super.onDestroy();
    }
}
