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
 *  - No localized page title is used.
 *  - The Shelter App Info fingerprint additionally requires the application
 *    identity "Shelter"; this is not a localized page title.
 *  - Matching is deterministic: there is no scoring or fuzzy matching.
 *
 * The structural fingerprints below are based on the supplied Android 16 /
 * Samsung accessibility captures.
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
            "com.android.settings:id/entity_header",
            "com.android.settings:id/entity_header_summary",
            "com.android.settings:id/entity_header_title",
            "com.android.settings:id/button1",
            "com.android.settings:id/button3",
            "com.android.settings:id/button4",
            "com.android.settings:id/recycler_view"
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
            "com.android.settings:id/sesl_switchbar_container",
            "com.android.settings:id/sesl_switchbar_switch",
            "com.android.settings:id/recycler_view",
            "android:id/title"
    );

    private static final Set<String> SHELTER_APP_INFO_INSTALLED_FORBIDDEN = set(
            "com.android.settings:id/security_dashboard_alert_center",
            "com.android.settings:id/switch_bar",
            "com.android.settings:id/sesl_switchbar_container"
    );

    private static final Set<String> SHELTER_APP_INFO_SUBSETTINGS_FORBIDDEN = set(
            "com.android.settings:id/security_dashboard_alert_center",
            "com.android.settings:id/switch_bar",
            "android:id/switch_widget",
            "com.android.settings:id/switch_widget",
            "com.android.settings:id/bottom_bar",
            "com.android.settings:id/button1",
            "com.android.settings:id/button3",
            "com.android.settings:id/button4",
            "com.android.settings:id/entity_header"
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

    /**
     * Language-independent page detector.
     *
     * IMPORTANT: visibleEventText is intentionally ignored.  No localized
     * page title is used for any security decision.
     *
     * The detector first uses the package/activity identity and then a small,
     * deterministic structural/resource-id fingerprint.  It does not use
     * scoring or OR-style fuzzy matching.
     */
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

        // Samsung Hidden Apps: unique activity identity; language-independent.
        if (LAUNCHER.equals(pkg) && APP_PICKER.equals(cls)) {
            return Page.HIDDEN_APPS;
        }

        if (!SETTINGS.equals(pkg)) {
            return Page.NONE;
        }

        /*
         * Shelter App Info has two observed Settings representations:
         *  1) InstalledAppDetailsTop with the entity-header structure;
         *  2) SubSettings with the Settings switch-bar structure and the
         *     application identity exposed in the accessibility tree.
         *
         * The application name "Shelter" is an application identity, not a
         * localized page title, so it is deliberately retained as the only
         * text check in this detector.
         */
        ScanResult scan = scanTree(root);
        Set<String> ids = scan.resourceIds;

        if (SETTINGS_INSTALLED_APP_DETAILS_TOP.equals(cls)
                && containsAll(ids, SHELTER_APP_INFO_REQUIRED)
                && containsNone(ids, SHELTER_APP_INFO_INSTALLED_FORBIDDEN)
                && scan.shelterEntityTitle) {
            return Page.SHELTER_APP_INFO;
        }

        if (SETTINGS_SUB_SETTINGS.equals(cls)
                && scan.shelterEntityTitle
                && containsAll(ids, SHELTER_APP_INFO_ACCESSIBILITY_REQUIRED)
                && containsNone(ids, SHELTER_APP_INFO_SUBSETTINGS_FORBIDDEN)) {
            return Page.SHELTER_APP_INFO;
        }

        if (!SETTINGS_SUB_SETTINGS.equals(cls)) {
            return Page.NONE;
        }

        /*
         * Developer options.
         * The Samsung switch-bar IDs are distinctive and are present in the
         * observed Developer-options accessibility tree.  A generic Android
         * switch widget is NOT sufficient because other Settings pages use it.
         */
        if (containsAll(ids, set(
                "com.android.settings:id/sesl_switchbar_container",
                "com.android.settings:id/sesl_switchbar_switch"))
                && containsNone(ids, set(
                "com.android.settings:id/security_dashboard_alert_center",
                "com.android.settings:id/entity_header",
                "com.android.settings:id/bottom_bar"))) {
            return Page.DEVELOPER_OPTIONS;
        }

        /*
         * Security & privacy.
         * These four Samsung Settings IDs were observed together on the
         * target Security & privacy page.  They are structural IDs, not text.
         */
        if (containsAll(ids, set(
                "com.android.settings:id/microphone_label",
                "com.android.settings:id/location_label",
                "com.android.settings:id/camera_label",
                "com.android.settings:id/used_duration"))
                && containsNone(ids, set(
                "com.android.settings:id/entity_header",
                "com.android.settings:id/sesl_switchbar_container",
                "com.android.settings:id/sesl_switchbar_switch"))) {
            return Page.SECURITY_PRIVACY;
        }

        /*
         * Device admin apps.
         * On the observed Android 16/Samsung build this page exposes the
         * common Settings list skeleton but no summary node and no switch
         * widget.  The absence conditions are mandatory so that the nearby
         * Accessibility-installed-apps and More-security pages do not match.
         */
        if (containsAll(ids, set(
                "com.android.settings:id/action_bar",
                "com.android.settings:id/collapsing_appbar_extended_title",
                "com.android.settings:id/recycler_view",
                "com.android.settings:id/title",
                "com.android.settings:id/coordinator"))
                && containsNone(ids, set(
                "android:id/summary",
                "android:id/switch_widget",
                "com.android.settings:id/switch_widget",
                "com.android.settings:id/sesl_switchbar_container",
                "com.android.settings:id/sesl_switchbar_switch",
                "com.android.settings:id/security_dashboard_alert_center",
                "com.android.settings:id/entity_header"))) {
            return Page.DEVICE_ADMIN_APPS;
        }

        /*
         * Accessibility -> Installed apps.
         * It has the same list skeleton as Device admin apps, but the observed
         * Accessibility page exposes android:id/summary.  A switch widget or
         * the Security-page IDs disqualify it.
         */
        if (containsAll(ids, set(
                "com.android.settings:id/action_bar",
                "com.android.settings:id/collapsing_appbar_extended_title",
                "com.android.settings:id/recycler_view",
                "com.android.settings:id/title",
                "com.android.settings:id/coordinator",
                "android:id/summary"))
                && containsNone(ids, set(
                "android:id/switch_widget",
                "com.android.settings:id/switch_widget",
                "com.android.settings:id/sesl_switchbar_container",
                "com.android.settings:id/sesl_switchbar_switch",
                "com.android.settings:id/security_dashboard_alert_center",
                "com.android.settings:id/entity_header"))) {
            return Page.ACCESSIBILITY_INSTALLED_APPS;
        }

        // visibleEventText is deliberately not consulted.
        return Page.NONE;
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
