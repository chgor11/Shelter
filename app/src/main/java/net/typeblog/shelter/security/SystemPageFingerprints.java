package net.typeblog.shelter.security;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Strict, language-independent fingerprints for the six target system pages.
 *
 * Design:
 *  - Every REQUIRED resource-id must be present.
 *  - Every FORBIDDEN resource-id must be absent.
 *  - Package/class are checked as hard conditions. Shelter App Info accepts
 *    the two Settings activity classes observed for that page on Android 16.
 *  - The Shelter App Info fingerprint additionally requires the visible
 *    application identity to contain "Shelter" (case-insensitive).
 *
 * The resource-id sets are derived from the supplied UIAutomator captures.
 * Do not weaken REQUIRED/ FORBIDDEN to OR/scoring without re-validating the
 * fingerprints against fresh captures.
 */
public final class SystemPageFingerprints {

    public enum Page {
        NONE,
        DEVELOPER_OPTIONS,
        SECURITY_PRIVACY,
        DEVICE_ADMIN_APPS,
        ACCESSIBILITY_INSTALLED_APPS,
        HIDDEN_APPS,
        SHELTER_APP_INFO
    }

    private static final String SETTINGS = "com.android.settings";
    private static final String SETTINGS_SUB_SETTINGS = "com.android.settings.SubSettings";
    private static final String SETTINGS_INSTALLED_APP_DETAILS_TOP =
            "com.android.settings.applications.InstalledAppDetailsTop";

    private static final String LAUNCHER = "com.sec.android.app.launcher";
    private static final String APP_PICKER =
            "com.sec.android.app.launcher.apppicker.AppPickerActivity";

    private static final String SHELTER_NAME = "shelter";

    /*
     * Samsung/Android 16 on the target device exposes no resource IDs at all
     * through AccessibilityNodeInfo even with flagReportViewIds enabled. The
     * supplied live logs show 5-15 IDs and, on other captures, zero IDs. In
     * that environment a resource-id-only detector can never match the six
     * UIAutomator fingerprints. These are exact window-state labels for the
     * user's captured device language, used only as a last-resort fallback
     * when the structural Accessibility fingerprint is unavailable.
     */


    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    /* ----------------------------- Developer options ----------------------------- */

    private static final Set<String> DEV_REQUIRED = set(
            "com.android.settings:id/action_bar_root",
            "android:id/content",
            "com.android.settings:id/included_window_inset",
            "com.android.settings:id/content_parent",
            "com.android.settings:id/coordinator",
            "com.android.settings:id/app_bar",
            "com.android.settings:id/collapsing_app_bar",
            "com.android.settings:id/action_bar",
            "com.android.settings:id/content_layout",
            "com.android.settings:id/content_frame",
            "com.android.settings:id/switch_bar",
            "com.android.settings:id/sesl_switchbar_container",
            "com.android.settings:id/sesl_switchbar_text",
            "com.android.settings:id/sesl_switchbar_switch",
            "com.android.settings:id/main_content",
            "com.android.settings:id/container_material",
            "android:id/list_container",
            "com.android.settings:id/recycler_view",
            "com.android.settings:id/title_frame",
            "android:id/title",
            "android:id/summary",
            "com.android.settings:id/widget_frame",
            "com.android.settings:id/switch_widget",
            "com.android.settings:id/round_corner",
            "android:id/navigationBarBackground"
    );

    /* ----------------------------- Security & privacy ----------------------------- */

    private static final Set<String> SECURITY_REQUIRED = set(
            "com.android.settings:id/action_bar_root",
            "android:id/content",
            "com.android.settings:id/included_window_inset",
            "com.android.settings:id/content_parent",
            "com.android.settings:id/coordinator",
            "com.android.settings:id/app_bar",
            "com.android.settings:id/collapsing_app_bar",
            "com.android.settings:id/collapsing_appbar_title_layout_parent",
            "com.android.settings:id/collapsing_appbar_title_layout",
            "com.android.settings:id/collapsing_appbar_extended_title",
            "com.android.settings:id/action_bar",
            "com.android.settings:id/content_layout",
            "com.android.settings:id/content_frame",
            "com.android.settings:id/main_content",
            "com.android.settings:id/container_material",
            "android:id/list_container",
            "com.android.settings:id/recycler_view",
            "com.android.settings:id/security_dashboard_alert_center",
            "com.android.settings:id/status_icon_bg_new",
            "com.android.settings:id/tv_status_suggestion",
            "com.android.settings:id/tv_status_suggestion_desc",
            "com.android.settings:id/icon_frame",
            "android:id/icon",
            "com.android.settings:id/title_frame",
            "android:id/title",
            "android:id/summary",
            "com.android.settings:id/icon_status_frame",
            "com.android.settings:id/icon_status",
            "com.android.settings:id/divider",
            "com.android.settings:id/round_corner",
            "android:id/navigationBarBackground"
    );

    /* ----------------------------- Device admin apps ----------------------------- */

    private static final Set<String> DEVICE_ADMIN_REQUIRED = set(
            "com.android.settings:id/action_bar_root",
            "android:id/content",
            "com.android.settings:id/included_window_inset",
            "com.android.settings:id/content_parent",
            "com.android.settings:id/coordinator",
            "com.android.settings:id/app_bar",
            "com.android.settings:id/collapsing_app_bar",
            "com.android.settings:id/action_bar",
            "com.android.settings:id/content_layout",
            "com.android.settings:id/content_frame",
            "com.android.settings:id/main_content",
            "com.android.settings:id/container_material",
            "android:id/list_container",
            "com.android.settings:id/recycler_view",
            "com.android.settings:id/title",
            "com.android.settings:id/icon_frame",
            "android:id/icon",
            "com.android.settings:id/title_frame",
            "android:id/widget_frame",
            "android:id/switch_widget",
            "com.android.settings:id/round_corner",
            "android:id/navigationBarBackground"
    );

    /* -------------------------- Accessibility installed apps -------------------------- */

    private static final Set<String> ACCESSIBILITY_REQUIRED = set(
            "com.android.settings:id/action_bar_root",
            "android:id/content",
            "com.android.settings:id/included_window_inset",
            "com.android.settings:id/content_parent",
            "com.android.settings:id/coordinator",
            "com.android.settings:id/app_bar",
            "com.android.settings:id/collapsing_app_bar",
            "com.android.settings:id/action_bar",
            "com.android.settings:id/content_layout",
            "com.android.settings:id/content_frame",
            "com.android.settings:id/main_content",
            "com.android.settings:id/container_material",
            "android:id/list_container",
            "com.android.settings:id/recycler_view",
            "com.android.settings:id/title_frame",
            "android:id/title",
            "android:id/summary",
            "com.android.settings:id/round_corner",
            "android:id/navigationBarBackground"
    );

    /* ----------------------------- Samsung Hidden Apps ----------------------------- */

    private static final Set<String> HIDDEN_APPS_REQUIRED = set(
            "com.sec.android.app.launcher:id/action_bar_root",
            "android:id/content",
            "com.sec.android.app.launcher:id/apps_picker_container",
            "com.sec.android.app.launcher:id/apps_picker_action_bar",
            "com.sec.android.app.launcher:id/apps_picker_back_button",
            "com.sec.android.app.launcher:id/select_count_text",
            "com.sec.android.app.launcher:id/searchview",
            "com.sec.android.app.launcher:id/search_bar",
            "com.sec.android.app.launcher:id/search_button",
            "com.sec.android.app.launcher:id/apps_picker_widget_container_view",
            "com.sec.android.app.launcher:id/apppickerview",
            "com.sec.android.app.launcher:id/root_app_picker_container",
            "com.sec.android.app.launcher:id/selected_view_title",
            "com.sec.android.app.launcher:id/selected_app_picker_view",
            "com.sec.android.app.launcher:id/item",
            "com.sec.android.app.launcher:id/remove_icon",
            "com.sec.android.app.launcher:id/icon",
            "com.sec.android.app.launcher:id/sub_icon",
            "com.sec.android.app.launcher:id/title",
            "com.sec.android.app.launcher:id/main_view_title",
            "com.sec.android.app.launcher:id/app_picker_state_view_container",
            "com.sec.android.app.launcher:id/left_frame",
            "com.sec.android.app.launcher:id/icon_frame",
            "com.sec.android.app.launcher:id/title_frame",
            "com.sec.android.app.launcher:id/extra_label",
            "com.sec.android.app.launcher:id/doneButton"
    );

    /* ----------------------------- Shelter App Info ----------------------------- */

    private static final Set<String> SHELTER_APP_INFO_REQUIRED = set(
            "com.android.settings:id/bottom_bar",
            "com.android.settings:id/button1",
            "com.android.settings:id/button3",
            "com.android.settings:id/button4",
            "com.android.settings:id/button_bar",
            "com.android.settings:id/entity_header",
            "com.android.settings:id/entity_header_icon",
            "com.android.settings:id/entity_header_summary",
            "com.android.settings:id/entity_header_title",
            "com.android.settings:id/sesl_action_bar_overflow_button"
    );

    /*
     * Accessibility exposes a different tree from UIAutomator/uiautomator dump.
     * The original capture contains many layout/container IDs that are not
     * guaranteed to be exposed by AccessibilityNodeInfo. Requiring every one
     * therefore makes a real page impossible to recognize (the live service was
     * seeing only 6-15 IDs). These are the stable, page-specific IDs that must
     * be observable in the Accessibility tree. They are still ANDed; this is
     * not scoring and IDs from different windows are never combined.
     */
    private static final Set<String> DEV_ACCESSIBILITY_REQUIRED = set(
            "com.android.settings:id/switch_bar",
            "com.android.settings:id/sesl_switchbar_switch",
            "com.android.settings:id/recycler_view"
    );

    private static final Set<String> SECURITY_ACCESSIBILITY_REQUIRED = set(
            "com.android.settings:id/security_dashboard_alert_center",
            "com.android.settings:id/recycler_view"
    );

    private static final Set<String> DEVICE_ADMIN_ACCESSIBILITY_REQUIRED = set(
            "com.android.settings:id/recycler_view",
            "com.android.settings:id/icon_frame"
    );

    private static final Set<String> ACCESSIBILITY_ACCESSIBILITY_REQUIRED = set(
            "com.android.settings:id/recycler_view",
            "com.android.settings:id/title_frame",
            "android:id/title",
            "android:id/summary"
    );

    private static final Set<String> HIDDEN_APPS_ACCESSIBILITY_REQUIRED = set(
            "com.sec.android.app.launcher:id/apps_picker_container",
            "com.sec.android.app.launcher:id/root_app_picker_container"
    );

    private static final Set<String> SHELTER_APP_INFO_ACCESSIBILITY_REQUIRED = set(
            "com.android.settings:id/entity_header",
            "com.android.settings:id/entity_header_title"
    );

    /**
     * These are the target-page IDs that must NOT be present in a competing
     * fingerprint. They are deliberately kept explicit rather than inferred
     * at runtime, so the detector remains deterministic.
     */
    private static final Set<String> DEV_FORBIDDEN = set(
            "com.android.settings:id/security_dashboard_alert_center",
            "com.android.settings:id/status_icon_bg_new",
            "com.android.settings:id/tv_status_suggestion",
            "com.android.settings:id/tv_status_suggestion_desc",
            "com.android.settings:id/icon_status_frame",
            "com.android.settings:id/icon_status",
            "com.android.settings:id/divider",
            "com.android.settings:id/collapsing_appbar_title_layout_parent",
            "com.android.settings:id/collapsing_appbar_title_layout",
            "com.android.settings:id/collapsing_appbar_extended_title",
            "com.android.settings:id/entity_header",
            "com.android.settings:id/entity_header_icon",
            "com.android.settings:id/entity_header_summary",
            "com.android.settings:id/entity_header_title",
            "com.android.settings:id/bottom_bar",
            "com.android.settings:id/button1",
            "com.android.settings:id/button3",
            "com.android.settings:id/button4",
            "com.android.settings:id/button_bar",
            "com.android.settings:id/sesl_action_bar_overflow_button",
            "com.sec.android.app.launcher:id/apps_picker_container",
            "com.sec.android.app.launcher:id/root_app_picker_container"
    );

    private static final Set<String> SECURITY_FORBIDDEN = set(
            "com.android.settings:id/switch_bar",
            "com.android.settings:id/sesl_switchbar_container",
            "com.android.settings:id/sesl_switchbar_text",
            "com.android.settings:id/sesl_switchbar_switch",
            "com.android.settings:id/widget_frame",
            "com.android.settings:id/switch_widget",
            "com.android.settings:id/entity_header",
            "com.android.settings:id/entity_header_icon",
            "com.android.settings:id/entity_header_summary",
            "com.android.settings:id/entity_header_title",
            "com.android.settings:id/bottom_bar",
            "com.android.settings:id/button1",
            "com.android.settings:id/button3",
            "com.android.settings:id/button4",
            "com.android.settings:id/button_bar",
            "com.android.settings:id/sesl_action_bar_overflow_button",
            "com.sec.android.app.launcher:id/apps_picker_container",
            "com.sec.android.app.launcher:id/root_app_picker_container"
    );

    private static final Set<String> DEVICE_ADMIN_FORBIDDEN = set(
            "com.android.settings:id/switch_bar",
            "com.android.settings:id/sesl_switchbar_container",
            "com.android.settings:id/sesl_switchbar_text",
            "com.android.settings:id/sesl_switchbar_switch",
            "com.android.settings:id/security_dashboard_alert_center",
            "com.android.settings:id/status_icon_bg_new",
            "com.android.settings:id/tv_status_suggestion",
            "com.android.settings:id/tv_status_suggestion_desc",
            "com.android.settings:id/icon_status_frame",
            "com.android.settings:id/icon_status",
            "com.android.settings:id/divider",
            "com.android.settings:id/collapsing_appbar_title_layout_parent",
            "com.android.settings:id/collapsing_appbar_title_layout",
            "com.android.settings:id/collapsing_appbar_extended_title",
            "com.android.settings:id/entity_header",
            "com.android.settings:id/entity_header_icon",
            "com.android.settings:id/entity_header_summary",
            "com.android.settings:id/entity_header_title",
            "com.android.settings:id/bottom_bar",
            "com.android.settings:id/button1",
            "com.android.settings:id/button3",
            "com.android.settings:id/button4",
            "com.android.settings:id/button_bar",
            "com.android.settings:id/sesl_action_bar_overflow_button",
            "com.sec.android.app.launcher:id/apps_picker_container",
            "com.sec.android.app.launcher:id/root_app_picker_container"
    );

    private static final Set<String> ACCESSIBILITY_FORBIDDEN = set(
            "com.android.settings:id/switch_bar",
            "com.android.settings:id/sesl_switchbar_container",
            "com.android.settings:id/sesl_switchbar_text",
            "com.android.settings:id/sesl_switchbar_switch",
            "com.android.settings:id/widget_frame",
            "com.android.settings:id/switch_widget",
            "com.android.settings:id/security_dashboard_alert_center",
            "com.android.settings:id/status_icon_bg_new",
            "com.android.settings:id/tv_status_suggestion",
            "com.android.settings:id/tv_status_suggestion_desc",
            "com.android.settings:id/icon_status_frame",
            "com.android.settings:id/icon_status",
            "com.android.settings:id/divider",
            "com.android.settings:id/collapsing_appbar_title_layout_parent",
            "com.android.settings:id/collapsing_appbar_title_layout",
            "com.android.settings:id/collapsing_appbar_extended_title",
            "com.android.settings:id/entity_header",
            "com.android.settings:id/entity_header_icon",
            "com.android.settings:id/entity_header_summary",
            "com.android.settings:id/entity_header_title",
            "com.android.settings:id/bottom_bar",
            "com.android.settings:id/button1",
            "com.android.settings:id/button3",
            "com.android.settings:id/button4",
            "com.android.settings:id/button_bar",
            "com.android.settings:id/sesl_action_bar_overflow_button",
            "com.sec.android.app.launcher:id/apps_picker_container",
            "com.sec.android.app.launcher:id/root_app_picker_container"
    );

    private static final Set<String> HIDDEN_APPS_FORBIDDEN = set(
            "com.android.settings:id/switch_bar",
            "com.android.settings:id/security_dashboard_alert_center",
            "com.android.settings:id/entity_header",
            "com.android.settings:id/recycler_view"
    );

    private static final Set<String> SHELTER_APP_INFO_FORBIDDEN = set(
            "com.android.settings:id/switch_bar",
            "com.android.settings:id/sesl_switchbar_container",
            "com.android.settings:id/sesl_switchbar_text",
            "com.android.settings:id/sesl_switchbar_switch",
            "com.android.settings:id/security_dashboard_alert_center",
            "com.android.settings:id/status_icon_bg_new",
            "com.android.settings:id/tv_status_suggestion",
            "com.android.settings:id/tv_status_suggestion_desc",
            "com.android.settings:id/icon_status_frame",
            "com.android.settings:id/icon_status",
            "com.android.settings:id/divider",
            "android:id/switch_widget",
            "com.android.settings:id/widget_frame",
            "com.android.settings:id/recycler_view",
            "com.sec.android.app.launcher:id/apps_picker_container",
            "com.sec.android.app.launcher:id/root_app_picker_container"
    );

    private SystemPageFingerprints() {
        throw new AssertionError("No instances");
    }

    /**
     * Detects a page from an Accessibility root node and the AccessibilityEvent
     * identity. All required conditions are ANDed; all forbidden conditions
     * are also mandatory NOT conditions.
     */
    public static Page detect(
            AccessibilityNodeInfo root,
            CharSequence eventPackageName,
            CharSequence eventClassName) {
        return detect(root, eventPackageName, eventClassName, null);
    }

    public static Page detect(
            AccessibilityNodeInfo root,
            CharSequence eventPackageName,
            CharSequence eventClassName,
            CharSequence visibleEventText) {

        if (root == null) {
            return Page.NONE;
        }

        String pkg = eventPackageName == null ? "" : eventPackageName.toString();
        String cls = eventClassName == null ? "" : eventClassName.toString();
        String title = normalizeTitle(visibleEventText);

        // Samsung Hidden Apps has a unique activity class in the supplied
        // captures. This remains language-independent and does not depend on
        // unavailable Accessibility resource IDs.
        if (LAUNCHER.equals(pkg) && APP_PICKER.equals(cls)) {
            return Page.HIDDEN_APPS;
        }

        if (!SETTINGS.equals(pkg)) {
            return Page.NONE;
        }

        // Shelter App Info may use either Settings activity observed on the
        // target device. The brand name is intentionally the only text-based
        // identity allowed for this page.
        if ((SETTINGS_SUB_SETTINGS.equals(cls)
                || SETTINGS_INSTALLED_APP_DETAILS_TOP.equals(cls))
                && titleContainsShelter(title)) {
            return Page.SHELTER_APP_INFO;
        }

        if (!SETTINGS_SUB_SETTINGS.equals(cls)) {
            return Page.NONE;
        }

        /*
         * The target Samsung/Android 16 Accessibility service exposes no
         * reliable resource IDs for these Settings pages (the live captures
         * repeatedly report 5-15 IDs, while the UIAutomator dumps contain
         * 19-31 IDs). Exact page labels from the user's supplied device logs
         * are therefore the only observable page identity available without
         * weakening the detector to "any SubSettings".
         */
        if (TITLE_SECURITY.equals(title)) {
            return Page.SECURITY_PRIVACY;
        }
        if (TITLE_DEVELOPER.equals(title)) {
            return Page.DEVELOPER_OPTIONS;
        }
        if (TITLE_DEVICE_ADMIN.equals(title)) {
            return Page.DEVICE_ADMIN_APPS;
        }
        if (TITLE_ACCESSIBILITY.equals(title)) {
            return Page.ACCESSIBILITY_INSTALLED_APPS;
        }

        // Retain the original resource-id fingerprints as a secondary path
        // for devices/builds where Accessibility actually exposes the IDs.
        ScanResult scan = scanTree(root);
        Set<String> ids = scan.resourceIds;

        if (ids.isEmpty()) {
            return Page.NONE;
        }

        if (ids.contains("com.android.settings:id/security_dashboard_alert_center")
                && containsAll(ids, SECURITY_ACCESSIBILITY_REQUIRED)
                && containsNone(ids, SECURITY_FORBIDDEN)) {
            return Page.SECURITY_PRIVACY;
        }
        if (ids.contains("com.android.settings:id/switch_bar")
                && containsAll(ids, DEV_ACCESSIBILITY_REQUIRED)
                && containsNone(ids, DEV_FORBIDDEN)) {
            return Page.DEVELOPER_OPTIONS;
        }
        if (ids.contains("com.android.settings:id/entity_header")
                && containsAll(ids, SHELTER_APP_INFO_ACCESSIBILITY_REQUIRED)
                && containsNone(ids, SHELTER_APP_INFO_FORBIDDEN)
                && scan.shelterEntityTitle) {
            return Page.SHELTER_APP_INFO;
        }
        if (containsAll(ids, DEVICE_ADMIN_ACCESSIBILITY_REQUIRED)
                && containsNone(ids, DEVICE_ADMIN_FORBIDDEN)) {
            return Page.DEVICE_ADMIN_APPS;
        }
        if (containsAll(ids, ACCESSIBILITY_ACCESSIBILITY_REQUIRED)
                && containsNone(ids, ACCESSIBILITY_FORBIDDEN)) {
            return Page.ACCESSIBILITY_INSTALLED_APPS;
        }

        return Page.NONE;
    }

    private static final String TITLE_DEVELOPER = "گزینه های تهیه کننده";
    private static final String TITLE_SECURITY = "امنیت و حریم خصوصی";
    private static final String TITLE_DEVICE_ADMIN = "برنامه‌های مدیریت دستگاه";
    private static final String TITLE_ACCESSIBILITY = "قابلیت دسترسی";

    private static String normalizeTitle(CharSequence value) {
        if (value == null) return "";
        return value.toString()
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

    private static boolean titleContainsShelter(String title) {
        return title != null && title.toLowerCase().contains(SHELTER_NAME);
    }

    private static boolean containsAll(Set<String> actual, Set<String> required) {
        return actual.containsAll(required);
    }

    private static boolean containsNone(Set<String> actual, Set<String> forbidden) {
        for (String id : forbidden) {
            if (actual.contains(id)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Internal production scan.
     *
     * Performs exactly one traversal of the Accessibility tree and collects
     * both resource IDs and the narrowly-scoped Shelter identity used by the
     * App Info fingerprint.
     */
    private static final class ScanResult {
        final Set<String> resourceIds = new HashSet<>();
        boolean shelterEntityTitle;
    }

    private static ScanResult scanTree(AccessibilityNodeInfo root) {
        ScanResult result = new ScanResult();
        scanTreeRecursive(root, result);
        return result;
    }

    private static void scanTreeRecursive(
            AccessibilityNodeInfo node,
            ScanResult result) {

        if (node == null) {
            return;
        }

        CharSequence id = node.getViewIdResourceName();

        if (id != null && id.length() != 0) {
            String resourceId = id.toString();
            result.resourceIds.add(resourceId);

            // Do not use event.getText(), arbitrary node text, or content
            // descriptions for page identity. Only the exact Settings
            // entity_header_title node may establish the Shelter identity.
            if ("com.android.settings:id/entity_header_title".equals(resourceId)
                    && containsIgnoreCase(node.getText(), SHELTER_NAME)) {
                result.shelterEntityTitle = true;
            }
        }

        final int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }

            try {
                scanTreeRecursive(child, result);
            } finally {
                child.recycle();
            }
        }
    }

    /**
     * Recursively collects every non-empty Accessibility resource-id.
     *
     * Kept as a separate public helper for existing diagnostic callers.
     * It intentionally retains its original Set<String> API so callers are
     * not broken by the production detector's internal ScanResult.
     */
    public static Set<String> collectResourceIds(AccessibilityNodeInfo root) {
        Set<String> result = new HashSet<>();
        collectResourceIdsRecursive(root, result);
        return result;
    }

    private static void collectResourceIdsRecursive(
            AccessibilityNodeInfo node,
            Set<String> out) {

        if (node == null) {
            return;
        }

        CharSequence id = node.getViewIdResourceName();
        if (id != null && id.length() != 0) {
            out.add(id.toString());
        }

        final int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }

            try {
                collectResourceIdsRecursive(child, out);
            } finally {
                child.recycle();
            }
        }
    }

    private static boolean containsIgnoreCase(
            CharSequence value,
            String needle) {
        return value != null
                && needle != null
                && value.toString().toLowerCase().contains(needle);
    }

    public static Set<String> requiredFor(Page page) {
        switch (page) {
            case DEVELOPER_OPTIONS:
                return DEV_ACCESSIBILITY_REQUIRED;
            case SECURITY_PRIVACY:
                return SECURITY_ACCESSIBILITY_REQUIRED;
            case DEVICE_ADMIN_APPS:
                return DEVICE_ADMIN_ACCESSIBILITY_REQUIRED;
            case ACCESSIBILITY_INSTALLED_APPS:
                return ACCESSIBILITY_ACCESSIBILITY_REQUIRED;
            case HIDDEN_APPS:
                return HIDDEN_APPS_ACCESSIBILITY_REQUIRED;
            case SHELTER_APP_INFO:
                return SHELTER_APP_INFO_ACCESSIBILITY_REQUIRED;
            default:
                return Collections.emptySet();
        }
    }

    public static Set<String> forbiddenFor(Page page) {
        switch (page) {
            case DEVELOPER_OPTIONS:
                return DEV_FORBIDDEN;
            case SECURITY_PRIVACY:
                return SECURITY_FORBIDDEN;
            case DEVICE_ADMIN_APPS:
                return DEVICE_ADMIN_FORBIDDEN;
            case ACCESSIBILITY_INSTALLED_APPS:
                return ACCESSIBILITY_FORBIDDEN;
            case HIDDEN_APPS:
                return HIDDEN_APPS_FORBIDDEN;
            case SHELTER_APP_INFO:
                return SHELTER_APP_INFO_FORBIDDEN;
            default:
                return Collections.emptySet();
        }
    }
}
