package net.typeblog.shelter.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import net.typeblog.shelter.R;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.util.SettingsManager;
import net.typeblog.shelter.util.Utility;

import java.util.Arrays;

public class SettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String SETTINGS_VERSION = "settings_version";
    private static final String SETTINGS_SOURCE_CODE = "settings_source_code";
    private static final String SETTINGS_TRANSLATE = "settings_translate";
    private static final String SETTINGS_BUG_REPORT = "settings_bug_report";
    private static final String SETTINGS_PATREON = "settings_patreon";
    private static final String SETTINGS_CROSS_PROFILE_FILE_CHOOSER =
            "settings_cross_profile_file_chooser";
    private static final String SETTINGS_BLOCK_CONTACTS_SEARCHING =
            "settings_block_contacts_searching";
    private static final String SETTINGS_AUTO_FREEZE_SERVICE =
            "settings_auto_freeze_service";
    private static final String SETTINGS_AUTO_FREEZE_DELAY =
            "settings_auto_freeze_delay";
    private static final String SETTINGS_SKIP_FOREGROUND =
            "settings_dont_freeze_foreground";
    private static final String SETTINGS_WORK_TO_PERSONAL_CLIPBOARD =
            "settings_work_to_personal_clipboard";
    private static final String SETTINGS_WORK_PROFILE_APP_INSTALL_UNINSTALL =
            "settings_work_profile_app_install_uninstall";
    private static final String SETTINGS_WORK_PROFILE_APPS_CONTROL =
            "settings_work_profile_apps_control";

    private static final int REQUEST_PARENT_KEYGUARD_POLICY = 4107;
    private String mPendingKeyguardPreferenceKey = null;
    private boolean mPendingKeyguardPreferenceValue = false;
    private boolean mPendingKeyguardPreferencePreviousValue = false;

    private static final int[] AUTO_FREEZE_DELAY_SECONDS =
            new int[]{0, 60, 2 * 60, 5 * 60};

    private SettingsManager mManager =
            SettingsManager.getInstance();

    private IShelterService mServiceWork = null;

    private CheckBoxPreference mPrefCrossProfileFileChooser = null;
    private CheckBoxPreference mPrefBlockContactsSearching = null;
    private CheckBoxPreference mPrefAutoFreezeService = null;
    private CheckBoxPreference mPrefSkipForeground = null;
    private CheckBoxPreference mPrefWorkToPersonalClipboard = null;
    private CheckBoxPreference mPrefWorkProfileAppInstallUninstall = null;
    private CheckBoxPreference mPrefWorkProfileAppsControl = null;

    private DropDownPreference mPrefAutoFreezeDelay = null;

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(
                view,
                savedInstanceState
        );

        ViewCompat.setOnApplyWindowInsetsListener(
                view.findViewById(
                        androidx.preference.R.id.recycler_view
                ),
                (v, windowInsets) -> {

                    Insets insets =
                            windowInsets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                            );

                    v.setPaddingRelative(
                            0,
                            0,
                            0,
                            insets.bottom
                    );

                    return WindowInsetsCompat.CONSUMED;
                }
        );
    }

    @Override
    public void onCreatePreferences(
            Bundle bundle,
            String s) {

        addPreferencesFromResource(
                R.xml.preferences_settings
        );
        android.util.Log.i("Shelter", "Loaded personal Settings preferences_settings.xml");

        /*
         * SECURITY POLICY ADMINISTRATION ENTRY
         *
         * Opens the separate Device Policy Manager page.
         *
         * IMPORTANT:
         * Security policies inside this page MUST NOT
         * be applied immediately.
         *
         * Required flow:
         *
         * Change
         *   ->
         * Pending Change
         *   ->
         * Review Summary
         *   ->
         * User Confirmation
         *   ->
         * Fresh Authentication
         *   ->
         * Apply Policy
         *
         * Adding direct policy execution here is forbidden.
         */

        Preference devicePolicyManager =
                findPreference("device_policy_manager");

        if (devicePolicyManager != null) {

            devicePolicyManager.setOnPreferenceClickListener(
                    preference -> {

                        requireActivity()
                                .getSupportFragmentManager()
                                .beginTransaction()
                                .replace(
                                        R.id.settings_fragment_container,
                                        new DevicePolicyManagerFragment(),
                                        "device_policy_manager"
                                )
                                .addToBackStack(
                                        "device_policy_manager"
                                )
                                .commit();

                        return true;
                    }
            );
        }

        mServiceWork =
                IShelterService.Stub.asInterface(
                        ((Bundle) getActivity()
                                .getIntent()
                                .getParcelableExtra("extras"))
                                .getBinder("profile_service")
                );

        // Set the displayed version
        try {

            findPreference(SETTINGS_VERSION)
                    .setSummary(
                            getContext()
                                    .getPackageManager()
                                    .getPackageInfo(
                                            getContext()
                                                    .getPackageName(),
                                            0
                                    )
                                    .versionName
                    );

        } catch (PackageManager.NameNotFoundException e) {
            // WTF?
        }

        // Open source code url on click
        findPreference(SETTINGS_SOURCE_CODE)
                .setOnPreferenceClickListener(
                        this::openSummaryUrl
                );

        findPreference(SETTINGS_BUG_REPORT)
                .setOnPreferenceClickListener(
                        this::openSummaryUrl
                );

        findPreference(SETTINGS_PATREON)
                .setOnPreferenceClickListener(
                        this::openSummaryUrl
                );

        findPreference(SETTINGS_TRANSLATE)
                .setOnPreferenceClickListener(
                        this::openSummaryUrl
                );

        // === Interactions ===

        mPrefCrossProfileFileChooser =
                (CheckBoxPreference) findPreference(
                        SETTINGS_CROSS_PROFILE_FILE_CHOOSER
                );

        mPrefCrossProfileFileChooser.setChecked(
                mManager.getCrossProfileFileChooserEnabled()
        );

        mPrefCrossProfileFileChooser
                .setOnPreferenceChangeListener(this);

        mPrefBlockContactsSearching =
                (CheckBoxPreference) findPreference(
                        SETTINGS_BLOCK_CONTACTS_SEARCHING
                );

        mPrefBlockContactsSearching.setChecked(
                mManager.getBlockContactsSearchingEnabled()
        );

        mPrefBlockContactsSearching
                .setOnPreferenceChangeListener(this);

        // Work Profile → Personal Profile clipboard
        mPrefWorkToPersonalClipboard =
                (CheckBoxPreference) findPreference(
                        SETTINGS_WORK_TO_PERSONAL_CLIPBOARD
                );

        mPrefWorkToPersonalClipboard.setChecked(
                mManager.getWorkToPersonalClipboardEnabled()
        );

        mPrefWorkToPersonalClipboard
                .setOnPreferenceChangeListener(this);

        // Allow install or uninstall app
        mPrefWorkProfileAppInstallUninstall =
                (CheckBoxPreference) findPreference(
                        SETTINGS_WORK_PROFILE_APP_INSTALL_UNINSTALL
                );

        mPrefWorkProfileAppInstallUninstall.setChecked(
                mManager.getWorkProfileAppInstallUninstallEnabled()
        );

        mPrefWorkProfileAppInstallUninstall
                .setOnPreferenceChangeListener(this);

        // Allow changing app controls in Work Profile
        mPrefWorkProfileAppsControl =
                (CheckBoxPreference) findPreference(
                        SETTINGS_WORK_PROFILE_APPS_CONTROL
                );

        mPrefWorkProfileAppsControl.setChecked(
                mManager.getWorkProfileAppsControlEnabled()
        );

        mPrefWorkProfileAppsControl
                .setOnPreferenceChangeListener(this);

        // === Services ===

        mPrefAutoFreezeService =
                (CheckBoxPreference) findPreference(
                        SETTINGS_AUTO_FREEZE_SERVICE
                );

        mPrefAutoFreezeService.setChecked(
                mManager.getAutoFreezeServiceEnabled()
        );

        mPrefAutoFreezeService
                .setOnPreferenceChangeListener(this);

        mPrefAutoFreezeDelay =
                findPreference(
                        SETTINGS_AUTO_FREEZE_DELAY
                );

        mPrefAutoFreezeDelay
                .setOnPreferenceChangeListener(this);

        mPrefAutoFreezeDelay.setEntries(
                Arrays.stream(AUTO_FREEZE_DELAY_SECONDS)
                        .mapToObj(
                                (it) -> getString(
                                        R.string.format_minutes,
                                        it / 60
                                )
                        )
                        .toArray(String[]::new)
        );

        mPrefAutoFreezeDelay.setEntryValues(
                Arrays.stream(AUTO_FREEZE_DELAY_SECONDS)
                        .mapToObj(String::valueOf)
                        .toArray(String[]::new)
        );

        updateAutoFreezeDelay();

        mPrefSkipForeground =
                (CheckBoxPreference) findPreference(
                        SETTINGS_SKIP_FOREGROUND
                );

        mPrefSkipForeground.setChecked(
                mManager.getSkipForegroundEnabled()
        );

        mPrefSkipForeground
                .setOnPreferenceChangeListener(this);

        // These are immediate controls on the main/personal Settings screen.
        // Handle the click explicitly so the checkbox does not depend on an
        // AndroidX change-listener return value or cross-profile Activity result.
        setupParentKeyguardPreference("settings_disable_parent_fingerprint");
        setupParentKeyguardPreference("settings_disable_parent_face");
        setupParentKeyguardPreference("settings_disable_parent_iris");
        setupParentKeyguardPreference("settings_disable_parent_trust_agents");

        // Disable FileShuttle on Q for now
        // Supported on R and beyond
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {

            mPrefCrossProfileFileChooser.setEnabled(
                    false
            );
        }

        // Disable FileShuttle for Android Go
        // as it requires SYSTEM_ALERT_WINDOW which
        // is not allowed on Go devices
        ActivityManager am =
                (ActivityManager) getContext()
                        .getSystemService(
                                Context.ACTIVITY_SERVICE
                        );

        if (am.isLowRamDevice()) {

            mPrefCrossProfileFileChooser.setEnabled(
                    false
            );
        }
    }

    @Override
    public void onResume() {

        super.onResume();

        // Update all preferences that may change when returning
        // i.e. preferences that open another dialog for picking
        updateAutoFreezeDelay();
    }

    private void updateAutoFreezeDelay() {

        mPrefAutoFreezeDelay.setSummary(
                getString(
                        R.string.format_minutes,
                        mManager.getAutoFreezeDelay() / 60
                )
        );
    }

    private boolean openSummaryUrl(
            Preference pref) {

        Intent intent =
                new Intent(Intent.ACTION_VIEW);

        intent.setData(
                Uri.parse(
                        pref.getSummary().toString()
                )
        );

        startActivity(intent);

        return true;
    }

    private void setupParentKeyguardPreference(String key) {
        CheckBoxPreference checkbox = findPreference(key);
        if (checkbox == null) {
            android.util.Log.e("Shelter", "Missing parent keyguard checkbox in preferences_settings.xml: " + key);
            return;
        }
        checkbox.setOnPreferenceChangeListener(null);
        checkbox.setOnPreferenceClickListener(clicked -> {
            CheckBoxPreference control = (CheckBoxPreference) clicked;
            // Preference click listeners run BEFORE CheckBoxPreference toggles
            // itself. Calculate the requested state as the inverse of its current state.
            boolean previousValue = control.isChecked();
            boolean enabled = !previousValue;
            android.util.Log.i("Shelter", "Parent keyguard checkbox clicked: key="
                    + key + ", previous=" + previousValue + ", requested=" + enabled);

            if (Utility.isProfileOwner(requireContext())) {
                android.util.Log.e("Shelter", "Refusing parent keyguard toggle from Work Profile instance: " + key);
                return true;
            }

            int policyFlag;
            switch (key) {
                case "settings_disable_parent_fingerprint":
                    policyFlag = android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT;
                    break;
                case "settings_disable_parent_face":
                    policyFlag = android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FACE;
                    break;
                case "settings_disable_parent_iris":
                    policyFlag = android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_IRIS;
                    break;
                case "settings_disable_parent_trust_agents":
                    policyFlag = android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS;
                    break;
                default:
                    android.util.Log.e("Shelter", "Unknown parent keyguard checkbox: " + key);
                    return true;
            }

            Intent request = new Intent(DummyActivity.SET_PARENT_KEYGUARD_POLICY);
            // DummyActivity declares CATEGORY_DEFAULT in its manifest filter. Add
            // the same category to the query intent so PackageManager can resolve
            // the registered forwarding activity for this action.
            request.addCategory(Intent.CATEGORY_DEFAULT);
            request.putExtra(DummyActivity.EXTRA_KEYGUARD_POLICY_FLAG, policyFlag);
            request.putExtra(DummyActivity.EXTRA_KEYGUARD_POLICY_ENABLED, enabled);
            try {
                // Preserve Shelter's established IPC route: resolve the other-profile
                // forwarding activity, then HMAC-sign the resolved request. Do not
                // bypass this helper with a locally targeted or implicit launch.
                Utility.transferIntentToProfile(requireContext(), request);
                mPendingKeyguardPreferenceKey = key;
                mPendingKeyguardPreferenceValue = enabled;
                mPendingKeyguardPreferencePreviousValue = previousValue;
                // Change the UI immediately on tap. If the Work Profile rejects the
                // request or cannot apply the policy, onActivityResult restores it.
                control.setChecked(enabled);
                startActivityForResult(request, REQUEST_PARENT_KEYGUARD_POLICY);
                android.util.Log.i("Shelter", "Started signed DummyActivity route for parent keyguard policy: key="
                        + key + ", enabled=" + enabled + ", component=" + request.getComponent());
            } catch (RuntimeException e) {
                control.setChecked(previousValue);
                mPendingKeyguardPreferenceKey = null;
                android.util.Log.e("Shelter", "Failed to dispatch parent keyguard policy request: key="
                        + key, e);
            }
            // Return true to consume the click; otherwise AndroidX performs a
            // second automatic toggle and can undo the explicit state above.
            return true;
        });
        android.util.Log.i("Shelter", "Registered parent keyguard click handler: " + key);
    }

    @Override
    public boolean onPreferenceChange(
            Preference preference,
            Object newState) {

        android.util.Log.i("Shelter", "Preference change callback: key="
                + preference.getKey() + ", newState=" + newState);
        if (preference ==
                mPrefCrossProfileFileChooser) {

            boolean enabled =
                    (boolean) newState;

            if (!enabled) {

                mManager.setCrossProfileFileChooserEnabled(
                        false
                );

                return true;
            }

            // The Work Profile provider reads files from the Personal Profile.
            // Therefore special storage/overlay access is needed only in the
            // Personal Profile instance; never request these permissions in Work.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && !Utility.isProfileOwner(requireContext())) {
                boolean hasPermission = ensureSpecialAccessPermission(
                        () -> {
                            try {
                                // When this UI runs in Work, mServiceWork is the
                                // Personal Profile service. Otherwise check locally.
                                return Utility.isProfileOwner(requireContext())
                                        ? mServiceWork.hasAllFileAccessPermission()
                                        : Utility.checkAllFileAccessPermission();
                            } catch (RemoteException e) {
                                return false;
                            }
                        },
                        R.string.request_storage_manager,
                        Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                );
                if (!hasPermission) {
                    return false;
                }

                hasPermission = ensureSpecialAccessPermission(
                        () -> {
                            try {
                                return Utility.isProfileOwner(requireContext())
                                        ? mServiceWork.hasSystemAlertPermission()
                                        : Utility.checkSystemAlertPermission(requireContext());
                            } catch (RemoteException e) {
                                return false;
                            }
                        },
                        R.string.request_system_alert,
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                );
                if (!hasPermission) {
                    return false;
                }
            }

            mManager.setCrossProfileFileChooserEnabled(
                    true
            );

            return true;

        } else if (preference ==
                mPrefBlockContactsSearching) {

            mManager.setBlockContactsSearchingEnabled(
                    (boolean) newState
            );

            return true;

        } else if (preference ==
                mPrefWorkToPersonalClipboard) {

            mManager.setWorkToPersonalClipboardEnabled(
                    (boolean) newState
            );

            return true;

        } else if (preference ==
                mPrefWorkProfileAppInstallUninstall) {

            mManager.setWorkProfileAppInstallUninstallEnabled(
                    (boolean) newState
            );

            return true;

        } else if (preference ==
                mPrefWorkProfileAppsControl) {

            mManager.setWorkProfileAppsControlEnabled(
                    (boolean) newState
            );

            return true;

        } else if (preference ==
                mPrefAutoFreezeService) {

            mManager.setAutoFreezeServiceEnabled(
                    (boolean) newState
            );

            return true;

        } else if (preference ==
                mPrefAutoFreezeDelay) {

            mManager.setAutoFreezeDelay(
                    Integer.parseInt(
                            (String) newState
                    )
            );

            updateAutoFreezeDelay();

            return true;

        } else if (preference ==
                mPrefSkipForeground) {

            boolean enabled =
                    (boolean) newState;

            if (!enabled) {

                mManager.setSkipForegroundEnabled(
                        false
                );

                return true;
            }

            boolean hasPermission =
                    ensureSpecialAccessPermission(
                            () -> {

                                try {

                                    return mServiceWork
                                            .hasUsageStatsPermission()
                                            && Utility
                                            .checkUsageStatsPermission(
                                                    getContext()
                                            );

                                } catch (RemoteException e) {

                                    return false;
                                }
                            },
                            R.string.request_usage_stats,
                            Settings.ACTION_USAGE_ACCESS_SETTINGS
                    );

            if (!hasPermission) {
                return false;
            }

            mManager.setSkipForegroundEnabled(
                    true
            );

            return true;

        } else {

            return false;
        }
    }

    private interface CheckPermissionCallback {
        boolean check();
    }

    private boolean ensureSpecialAccessPermission(
            CheckPermissionCallback checkPermission,
            int alertRes,
            String settingsAction) {

        if (!checkPermission.check()) {

        AlertDialog dialog = new AlertDialog.Builder(
                getContext()
        )
                .setMessage(alertRes)
                .setPositiveButton(
                        android.R.string.ok,
                        (dialogInterface, which) ->
                                startActivity(
                                        new Intent(settingsAction)
                                )
                )
                .setNegativeButton(
                        android.R.string.cancel,
                        (dialogInterface, which) ->
                                dialogInterface.dismiss()
                )
                .create();
        
        SecureActivity.secureDialog(dialog);
        dialog.show();

            return false;

        } else {

            return true;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PARENT_KEYGUARD_POLICY) {
            return;
        }
        String key = mPendingKeyguardPreferenceKey;
        boolean value = mPendingKeyguardPreferenceValue;
        boolean previousValue = mPendingKeyguardPreferencePreviousValue;
        mPendingKeyguardPreferenceKey = null;
        if (key == null) {
            android.util.Log.e("Shelter", "Received parent keyguard result without a pending checkbox request");
            return;
        }
        CheckBoxPreference preference = findPreference(key);
        if (resultCode == android.app.Activity.RESULT_OK) {
            if (preference != null) {
                preference.setChecked(value);
            }
            android.util.Log.i("Shelter", "Parent keyguard policy confirmed: key=" + key + ", enabled=" + value);
        } else {
            if (preference != null) {
                preference.setChecked(previousValue);
            }
            android.util.Log.e("Shelter", "Parent keyguard policy was not confirmed; restored checkbox: key=" + key
                    + ", resultCode=" + resultCode);
        }
    }

}
