package net.typeblog.shelter.security;

import android.os.LocaleList;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Strict, language-independent fingerprints for the six target system pages.
 *
 * Design:
 *  - Every REQUIRED resource-id must be present.
 *  - Every FORBIDDEN resource-id must be absent.
 *  - Package/class are checked as hard conditions. Shelter App Info accepts
 *    the two Settings activity classes observed for that page on Android 16.
 *  - Complete fingerprints do not depend on localized page titles.
 *  - An About Phone title is considered only as a final exception after a
 *    page has already become a protected-page candidate.
 *  - Incomplete Accessibility trees use a language-aware fallback:
 *      * system language other than Persian/English -> lock
 *      * Persian/English -> compare event/page title
 *      * exact About Phone title -> do not lock
 *      * otherwise -> lock
 *
 * Matching is deterministic. There is no scoring.
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
    private static final String SETTINGS_SUB_SETTINGS =
            "com.android.settings.SubSettings";
    private static final String SETTINGS_INSTALLED_APP_DETAILS_TOP =
            "com.android.settings.applications.InstalledAppDetailsTop";

    private static final String LAUNCHER = "com.sec.android.app.launcher";
    private static final String APP_PICKER =
            "com.sec.android.app.launcher.apppicker.AppPickerActivity";

    private static final String SHELTER_NAME = "shelter";

    /*
     * ============================================================
     * INCOMPLETE-PAGE TITLE FALLBACK
     * ============================================================
     *
     * These titles are used ONLY when the normal structural
     * fingerprint is incomplete.
     *
     * They do NOT participate in the normal language-independent
     * fingerprint matching.
     *
     * Keep both Persian and English forms here.
     */

    private static final Set<String> DEVELOPER_OPTIONS_TITLES = set(
            "Developer options",
            "گزینه‌های برنامه‌نویس"
    );

    private static final Set<String> SECURITY_PRIVACY_TITLES = set(
            "Security and privacy",
            "Security & privacy",
            "امنیت و حریم خصوصی"
    );

    private static final Set<String> DEVICE_ADMIN_APPS_TITLES = set(
            "Device admin apps",
            "Device administrators",
            "مدیران دستگاه",
            "برنامه‌های مدیریت دستگاه"
    );

    private static final Set<String> ACCESSIBILITY_INSTALLED_APPS_TITLES = set(
            "Installed apps",
            "Accessibility installed apps",
            "برنامه‌های نصب‌شده",
            "برنامه‌های نصب شده"
    );

    private static final Set<String> HIDDEN_APPS_TITLES = set(
            "Hidden apps",
            "برنامه‌های مخفی"
    );

    private static final Set<String> SHELTER_APP_INFO_TITLES = set(
            "Shelter",
            "شلتر"
    );

    /*
     * ============================================================
     * ABOUT PHONE EXCEPTION
     * ============================================================
     *
     * IMPORTANT:
     *
     * About Phone is NOT a protected page.
     *
     * It is checked ONLY AFTER another part of the detector has
     * already classified the current page as a protected-page
     * candidate.
     *
     * No resource-id is required.
     *
     * The exception is active only when the current SYSTEM language
     * is Persian or English.
     */
    private static final Set<String> ABOUT_PHONE_TITLES = set(
            "About Phone",
            "درباره تلفن"
    );

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(values))
        );
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
            "com.android.settings:id/security_dashboard_alert_center"
    );

    /*
     * Secondary Security & Privacy structural fingerprint.
     * This is an additional route/fingerprint and does not replace
     * SECURITY_REQUIRED.
     */
    private static final Set<String> SECURITY_SECONDARY_REQUIRED = set(
            "com.android.settings:id/security_dashboard_alert_center",
            "com.android.settings:id/recycler_view"
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
     * ============================================================
     * INDEPENDENT SECONDARY FINGERPRINTS
     * ============================================================
     *
     * These are deliberately separate from the original fingerprints.
     * The original fingerprints above are not modified.
     *
     * A secondary match is mapped back to the same primary Page value,
     * so callers still receive DEVICE_ADMIN_APPS or SHELTER_APP_INFO.
     */

    /*
     * Device Admin Apps secondary route:
     *
     * This route is based on the Accessibility snapshot observed by
     * PageProbe for Settings/SubSettings, plus the page title.
     * It is intentionally independent from the large primary
     * DEVICE_ADMIN_REQUIRED fingerprint.
     */
    private static final Set<String> DEVICE_ADMIN_SECONDARY_REQUIRED = set(
            "com.android.settings:id/action_bar",
            "com.android.settings:id/collapsing_appbar_extended_title",
            "com.android.settings:id/recycler_view",
            "com.android.settings:id/title",
            "com.android.settings:id/coordinator"
    );

    /*
     * Shelter App Info secondary route for InstalledAppDetailsTop.
     *
     * The primary route requires the Shelter name specifically on
     * entity_header_title. The secondary route instead verifies the
     * complete app-info structural group and searches the whole
     * Accessibility tree for the Shelter identity in text/content
     * descriptions. The original route remains unchanged.
     */
    private static final Set<String> SHELTER_APP_INFO_SECONDARY_INSTALLED_REQUIRED = set(
            "com.android.settings:id/entity_header",
            "com.android.settings:id/entity_header_summary",
            "com.android.settings:id/entity_header_title",
            "com.android.settings:id/button1",
            "com.android.settings:id/button3",
            "com.android.settings:id/button4",
            "com.android.settings:id/recycler_view"
    );

    /*
     * Shelter App Info secondary route for SubSettings.
     *
     * This route can use the visible page title/event title "Shelter"
     * without requiring that the text be attached specifically to
     * entity_header_title.
     */
    private static final Set<String> SHELTER_APP_INFO_SECONDARY_SUBSETTINGS_REQUIRED = set(
            "com.android.settings:id/action_bar",
            "com.android.settings:id/collapsing_appbar_extended_title",
            "com.android.settings:id/recycler_view",
            "com.android.settings:id/coordinator",
            "android:id/title"
    );

    /*
     * Accessibility exposes a different tree from UIAutomator/uiautomator dump.
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

    private static final Set<String> SHELTER_APP_INFO_SECONDARY_REQUIRED = set(
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
     * Original 3-argument API.
     */
    public static Page detect(
            AccessibilityNodeInfo root,
            CharSequence eventPackageName,
            CharSequence eventClassName) {

        return detect(root, eventPackageName, eventClassName, null);
    }

    /**
     * Main detector.
     *
     * The complete structural fingerprints are evaluated first.
     *
     * If one of the six protected pages is confidently identified,
     * About Phone is checked as the final exception.
     *
     * If the tree is incomplete, the language-aware fallback may be
     * used for a suspicious Settings/SubSettings page.
     */
    public static Page detect(
            AccessibilityNodeInfo root,
            CharSequence eventPackageName,
            CharSequence eventClassName,
            CharSequence visibleEventText) {

        if (root == null) {
            return Page.NONE;
        }

        String pkg = eventPackageName == null
                ? ""
                : eventPackageName.toString();

        String cls = eventClassName == null
                ? ""
                : eventClassName.toString();

        /*
         * Samsung Hidden Apps.
         *
         * Activity identity itself is sufficiently distinctive.
         */
        if (LAUNCHER.equals(pkg) && APP_PICKER.equals(cls)) {
            return Page.HIDDEN_APPS;
        }

        if (!SETTINGS.equals(pkg)) {
            return Page.NONE;
        }

        ScanResult scan = scanTree(root);
        Set<String> ids = scan.resourceIds;

        /*
         * ============================================================
         * INDEPENDENT SECONDARY FINGERPRINT ROUTES
         * ============================================================
         *
         * These routes are evaluated separately from all original
         * fingerprints below. A successful match is reported using
         * the ORIGINAL Page enum value.
         */

        /*
         * ------------------------------------------------------------
         * Secondary: Device admin apps
         * ------------------------------------------------------------
         */
        if (matchesDeviceAdminSecondary(
                root,
                cls,
                visibleEventText,
                ids)) {

            return applyAboutPhoneException(
                    Page.DEVICE_ADMIN_APPS,
                    root,
                    visibleEventText
            );
        }

        /*
         * ------------------------------------------------------------
         * Secondary: Shelter App Info
         * ------------------------------------------------------------
         */
        if (matchesShelterAppInfoSecondary(
                root,
                cls,
                visibleEventText,
                ids)) {

            return applyAboutPhoneException(
                    Page.SHELTER_APP_INFO,
                    root,
                    visibleEventText
            );
        }

        /*
         * ------------------------------------------------------------
         * Shelter App Info - InstalledAppDetailsTop
         * ------------------------------------------------------------
         */
        if (SETTINGS_INSTALLED_APP_DETAILS_TOP.equals(cls)
                && containsAll(ids, SHELTER_APP_INFO_REQUIRED)
                && containsNone(ids, SHELTER_APP_INFO_INSTALLED_FORBIDDEN)
                && scan.shelterEntityTitle) {

            return applyAboutPhoneException(
                    Page.SHELTER_APP_INFO,
                    root,
                    visibleEventText
            );
        }

        /*
         * ------------------------------------------------------------
         * Shelter App Info - SubSettings
         * ------------------------------------------------------------
         */
        if (SETTINGS_SUB_SETTINGS.equals(cls)
                && scan.shelterEntityTitle
                && containsAll(ids, SHELTER_APP_INFO_ACCESSIBILITY_REQUIRED)
                && containsNone(ids, SHELTER_APP_INFO_SUBSETTINGS_FORBIDDEN)) {

            return applyAboutPhoneException(
                    Page.SHELTER_APP_INFO,
                    root,
                    visibleEventText
            );
        }

        /*
         * A non-SubSettings Settings activity cannot be one of the
         * ordinary protected Settings pages below.
         */
        if (!SETTINGS_SUB_SETTINGS.equals(cls)) {
            return Page.NONE;
        }

        /*
         * ------------------------------------------------------------
         * Developer options
         * ------------------------------------------------------------
         */
        if (containsAll(ids, set(
                "com.android.settings:id/sesl_switchbar_container",
                "com.android.settings:id/sesl_switchbar_switch"))
                && containsNone(ids, set(
                "com.android.settings:id/security_dashboard_alert_center",
                "com.android.settings:id/entity_header",
                "com.android.settings:id/bottom_bar"))) {

            return applyAboutPhoneException(
                    Page.DEVELOPER_OPTIONS,
                    root,
                    visibleEventText
            );
        }

        /*
         * ------------------------------------------------------------
         * Security & privacy - primary structural fingerprint
         * ------------------------------------------------------------
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

            return applyAboutPhoneException(
                    Page.SECURITY_PRIVACY,
                    root,
                    visibleEventText
            );
        }

        /*
         * ------------------------------------------------------------
         * Security & privacy - secondary fingerprint
         * ------------------------------------------------------------
         *
         * This is intentionally checked in addition to the original
         * Security fingerprint.
         */
        if (containsAll(ids, SECURITY_SECONDARY_REQUIRED)
                && containsNone(ids, SECURITY_FORBIDDEN)) {

            return applyAboutPhoneException(
                    Page.SECURITY_PRIVACY,
                    root,
                    visibleEventText
            );
        }

        /*
         * ------------------------------------------------------------
         * Device admin apps
         * ------------------------------------------------------------
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

            return applyAboutPhoneException(
                    Page.DEVICE_ADMIN_APPS,
                    root,
                    visibleEventText
            );
        }

        /*
         * ------------------------------------------------------------
         * Accessibility -> Installed apps
         * ------------------------------------------------------------
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

            return applyAboutPhoneException(
                    Page.ACCESSIBILITY_INSTALLED_APPS,
                    root,
                    visibleEventText
            );
        }

        /*
         * ------------------------------------------------------------
         * Incomplete Accessibility tree fallback
         * ------------------------------------------------------------
         *
         * At this point no complete structural fingerprint matched.
         *
         * We only use the fallback for a Settings/SubSettings page.
         *
         * System language:
         *
         *   Persian / English:
         *       title/eventText is checked.
         *
         *   Anything else:
         *       suspicious incomplete page is treated as protected
         *       and the caller receives a protected Page result.
         *
         * IMPORTANT:
         * About Phone exception is applied here too.
         */
        return detectIncompleteSettingsPage(
                root,
                cls,
                visibleEventText
        );
    }

    /*
     * ------------------------------------------------------------
     * Independent secondary fingerprint: Device Admin Apps
     * ------------------------------------------------------------
     */
    private static boolean matchesDeviceAdminSecondary(
            AccessibilityNodeInfo root,
            String cls,
            CharSequence visibleEventText,
            Set<String> ids) {

        if (!SETTINGS_SUB_SETTINGS.equals(cls)) {
            return false;
        }

        if (!containsAll(ids, DEVICE_ADMIN_SECONDARY_REQUIRED)) {
            return false;
        }

        /*
         * Prefer the event title because it is supplied directly by
         * the Accessibility event. If it is absent, inspect visible
         * node text/content-description values in the same root.
         */
        return matchesAny(
                normalizedTitle(visibleEventText),
                DEVICE_ADMIN_APPS_TITLES
        ) || containsAnyTitleInTree(
                root,
                DEVICE_ADMIN_APPS_TITLES
        );
    }

    /*
     * ------------------------------------------------------------
     * Independent secondary fingerprint: Shelter App Info
     * ------------------------------------------------------------
     */
    private static boolean matchesShelterAppInfoSecondary(
            AccessibilityNodeInfo root,
            String cls,
            CharSequence visibleEventText,
            Set<String> ids) {

        if (SETTINGS_INSTALLED_APP_DETAILS_TOP.equals(cls)) {

            if (!containsAll(
                    ids,
                    SHELTER_APP_INFO_SECONDARY_INSTALLED_REQUIRED)) {
                return false;
            }

            /*
             * Unlike the original fingerprint, do not require the
             * Shelter text to belong specifically to
             * entity_header_title.
             */
            return containsShelterIdentityInTree(root);
        }

        if (SETTINGS_SUB_SETTINGS.equals(cls)) {

            if (!containsAll(
                    ids,
                    SHELTER_APP_INFO_SECONDARY_SUBSETTINGS_REQUIRED)) {
                return false;
            }

            /*
             * For the SubSettings route, the visible page title is a
             * strong identity marker when combined with this structure.
             */
            return matchesAny(
                    normalizedTitle(visibleEventText),
                    SHELTER_APP_INFO_TITLES
            ) || containsAnyTitleInTree(
                    root,
                    SHELTER_APP_INFO_TITLES
            );
        }

        return false;
    }

    private static boolean containsAnyTitleInTree(
            AccessibilityNodeInfo node,
            Set<String> candidates) {

        if (node == null) {
            return false;
        }

        if (matchesAny(
                normalizedTitle(node.getText()),
                candidates)) {
            return true;
        }

        if (matchesAny(
                normalizedTitle(node.getContentDescription()),
                candidates)) {
            return true;
        }

        final int childCount = node.getChildCount();

        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);

            if (child == null) {
                continue;
            }

            try {
                if (containsAnyTitleInTree(child, candidates)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }

        return false;
    }

    private static boolean containsShelterIdentityInTree(
            AccessibilityNodeInfo node) {

        if (node == null) {
            return false;
        }

        if (containsIgnoreCase(node.getText(), SHELTER_NAME)
                || containsIgnoreCase(
                node.getContentDescription(),
                SHELTER_NAME)) {
            return true;
        }

        final int childCount = node.getChildCount();

        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);

            if (child == null) {
                continue;
            }

            try {
                if (containsShelterIdentityInTree(child)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }

        return false;
    }

    /**
     * Handles an incomplete Settings/SubSettings tree.
     */
    private static Page detectIncompleteSettingsPage(
            AccessibilityNodeInfo root,
            String cls,
            CharSequence visibleEventText) {

        if (!SETTINGS_SUB_SETTINGS.equals(cls)) {
            return Page.NONE;
        }

        /*
         * We need some indication that this is actually a suspicious
         * incomplete Settings page rather than an arbitrary empty tree.
         *
         * These are generic structural markers already observed in the
         * supplied Android 16 captures.
         */
        ScanResult scan = scanTree(root);
        Set<String> ids = scan.resourceIds;

        boolean suspiciousSettingsStructure =
                ids.contains("com.android.settings:id/action_bar")
                        || ids.contains(
                        "com.android.settings:id/collapsing_appbar_extended_title")
                        || ids.contains(
                        "com.android.settings:id/recycler_view")
                        || ids.contains("com.android.settings:id/coordinator")
                        || ids.contains("android:id/title")
                        || ids.contains("android:id/summary");

        if (!suspiciousSettingsStructure) {
            return Page.NONE;
        }

        /*
         * First determine the current SYSTEM language.
         */
        String language = getSystemLanguage();

        /*
         * Any language other than Persian or English:
         *
         * The incomplete protected-page case is fail-closed.
         *
         * The returned enum is only used as the "protected candidate"
         * signal by the caller; it does not claim that the exact page
         * was structurally identified as Security & privacy.
         */
        if (!isPersianOrEnglish(language)) {
            return Page.SECURITY_PRIVACY;
        }

        /*
         * Persian or English:
         *
         * First check the explicit About Phone exception.
         *
         * If it is About Phone, return NONE.
         */
        if (matchesAboutPhoneTitle(root, visibleEventText)) {
            return Page.NONE;
        }

        /*
         * Otherwise identify the incomplete page by its localized
         * title/event text.
         */
        String title = normalizedTitle(visibleEventText);

        if (matchesAny(title, DEVELOPER_OPTIONS_TITLES)) {
            return Page.DEVELOPER_OPTIONS;
        }

        if (matchesAny(title, SECURITY_PRIVACY_TITLES)) {
            return Page.SECURITY_PRIVACY;
        }

        if (matchesAny(title, DEVICE_ADMIN_APPS_TITLES)) {
            return Page.DEVICE_ADMIN_APPS;
        }

        if (matchesAny(title, ACCESSIBILITY_INSTALLED_APPS_TITLES)) {
            return Page.ACCESSIBILITY_INSTALLED_APPS;
        }

        if (matchesAny(title, HIDDEN_APPS_TITLES)) {
            return Page.HIDDEN_APPS;
        }

        if (matchesAny(title, SHELTER_APP_INFO_TITLES)) {
            return Page.SHELTER_APP_INFO;
        }

        /*
         * Persian/English but no protected-page title:
         * do NOT lock.
         */
        return Page.NONE;
    }

    /**
     * Final About Phone exception.
     *
     * This method is called ONLY after another detector has already
     * classified the current page as one of the protected pages.
     *
     * If the system language is not Persian or English, About Phone
     * does NOT create an exception and the protected page remains
     * protected.
     */
    private static Page applyAboutPhoneException(
            Page protectedPage,
            AccessibilityNodeInfo root,
            CharSequence visibleEventText) {

        if (protectedPage == Page.NONE) {
            return Page.NONE;
        }

        String language = getSystemLanguage();

        /*
         * Non-Persian / non-English:
         * About Phone exception is disabled.
         */
        if (!isPersianOrEnglish(language)) {
            return protectedPage;
        }

        /*
         * Persian / English:
         * exact About Phone title/event text is the exception.
         */
        if (matchesAboutPhoneTitle(root, visibleEventText)) {
            return Page.NONE;
        }

        /*
         * It was already identified as a protected page and it is
         * not About Phone.
         */
        return protectedPage;
    }

    /**
     * Returns the first system locale language.
     *
     * LocaleList.getDefault() represents the device/system locale list.
     */
    private static String getSystemLanguage() {
        LocaleList locales = LocaleList.getDefault();

        if (locales == null || locales.isEmpty()) {
            return "";
        }

        Locale locale = locales.get(0);

        if (locale == null) {
            return "";
        }

        return locale.getLanguage().toLowerCase(Locale.ROOT);
    }

    private static boolean isPersianOrEnglish(String language) {
        return "fa".equals(language) || "en".equals(language);
    }

    /**
     * Checks eventText first, then searches node text for an exact
     * About Phone title.
     *
     * Resource IDs are deliberately NOT used here.
     */
    private static boolean matchesAboutPhoneTitle(
            AccessibilityNodeInfo root,
            CharSequence visibleEventText) {

        String eventTitle = normalizedTitle(visibleEventText);

        if (ABOUT_PHONE_TITLES.contains(eventTitle)) {
            return true;
        }

        return containsAboutPhoneTitleInTree(root);
    }

    private static boolean containsAboutPhoneTitleInTree(
            AccessibilityNodeInfo node) {

        if (node == null) {
            return false;
        }

        if (matchesAboutPhoneValue(node.getText())) {
            return true;
        }

        if (matchesAboutPhoneValue(node.getContentDescription())) {
            return true;
        }

        final int childCount = node.getChildCount();

        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);

            if (child == null) {
                continue;
            }

            try {
                if (containsAboutPhoneTitleInTree(child)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }

        return false;
    }

    private static boolean matchesAboutPhoneValue(CharSequence value) {
        if (value == null) {
            return false;
        }

        String normalized = normalizedTitle(value);
        return ABOUT_PHONE_TITLES.contains(normalized);
    }

    /**
     * Normalizes only surrounding whitespace.
     *
     * No fuzzy matching is performed.
     */
    private static String normalizedTitle(CharSequence value) {
        if (value == null) {
            return "";
        }

        return value.toString().trim();
    }

    private static boolean matchesAny(
            String value,
            Set<String> candidates) {

        if (value == null || value.isEmpty()) {
            return false;
        }

        return candidates.contains(value);
    }

    private static boolean containsAll(
            Set<String> actual,
            Set<String> required) {

        return actual.containsAll(required);
    }

    private static boolean containsNone(
            Set<String> actual,
            Set<String> forbidden) {

        for (String id : forbidden) {
            if (actual.contains(id)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Internal production scan.
     */
    private static final class ScanResult {

        final Set<String> resourceIds = new HashSet<>();

        boolean shelterEntityTitle;
    }

    private static ScanResult scanTree(
            AccessibilityNodeInfo root) {

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

            /*
             * Only the exact Settings entity_header_title node can
             * establish the Shelter application identity.
             */
            if ("com.android.settings:id/entity_header_title"
                    .equals(resourceId)
                    && containsIgnoreCase(
                    node.getText(),
                    SHELTER_NAME)) {

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
     * Existing public helper retained for compatibility.
     */
    public static Set<String> collectResourceIds(
            AccessibilityNodeInfo root) {

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
                && value.toString()
                .toLowerCase(Locale.ROOT)
                .contains(needle.toLowerCase(Locale.ROOT));
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
