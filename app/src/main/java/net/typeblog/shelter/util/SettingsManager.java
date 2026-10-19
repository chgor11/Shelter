package net.typeblog.shelter.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.app.admin.DevicePolicyManager;
import android.os.Process;

import net.typeblog.shelter.ui.DummyActivity;
import net.typeblog.shelter.util.AuthenticationUtility;

public class SettingsManager {
    private static SettingsManager sInstance = null;

    public static void initialize(Context context) {
        sInstance = new SettingsManager(context);
    }

    public static SettingsManager getInstance() {
        return sInstance;
    }

    private LocalStorageManager mStorage = LocalStorageManager.getInstance();
    private Context mContext;

    private SettingsManager(Context context) {
        mContext = context;
    }

    private void syncSettingsToProfileBool(String name, boolean value) {
        Intent intent = new Intent(DummyActivity.SYNCHRONIZE_PREFERENCE);
        intent.putExtra("name", name);
        intent.putExtra("boolean", value);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Utility.transferIntentToProfile(mContext, intent);
        mContext.startActivity(intent);
    }

    private void syncSettingsToProfileInt(String name, int value) {
        Intent intent = new Intent(DummyActivity.SYNCHRONIZE_PREFERENCE);
        intent.putExtra("name", name);
        intent.putExtra("int", value);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Utility.transferIntentToProfile(mContext, intent);
        mContext.startActivity(intent);
    }

    // Enforce all settings
    public void applyAll() {
        applyCrossProfileFileChooser();
    }

    // Read and apply the enabled state of the cross profile file chooser
    public void applyCrossProfileFileChooser() {
        boolean enabled = mStorage.getBoolean(LocalStorageManager.PREF_CROSS_PROFILE_FILE_CHOOSER);
        // The provider must exist only in the Work Profile. It exposes the
        // Personal Profile's storage to Work Profile document/media pickers.
        // Enabling it in the Personal Profile would create the reverse route
        // (Personal -> Work), which is intentionally forbidden.
        boolean enableProvider = enabled && Utility.isProfileOwner(mContext);
        mContext.getPackageManager().setComponentEnabledSetting(
                new ComponentName(mContext, CrossProfileDocumentsProvider.class),
                enableProvider ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    // Set the enabled state of the cross profile file chooser
    public void setCrossProfileFileChooserEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_CROSS_PROFILE_FILE_CHOOSER, enabled);
        applyCrossProfileFileChooser();
        syncSettingsToProfileBool(LocalStorageManager.PREF_CROSS_PROFILE_FILE_CHOOSER, enabled);
    }

    // Get the enabled state of the cross profile file chooser
    public boolean getCrossProfileFileChooserEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_CROSS_PROFILE_FILE_CHOOSER);
    }

    // Set the blocked state of cross-profile contacts searching
    public void setBlockContactsSearchingEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_BLOCK_CONTACTS_SEARCHING, enabled);
        syncSettingsToProfileBool(LocalStorageManager.PREF_BLOCK_CONTACTS_SEARCHING, enabled);
    }

    // Get the blocked state of cross-profile contacts searching
    public boolean getBlockContactsSearchingEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_BLOCK_CONTACTS_SEARCHING);
    }

    /**
     * Dispatch an independent parent lock-screen policy change through the
     * existing signed DummyActivity cross-profile route. The Work Profile
     * Profile Owner applies the corresponding KEYGUARD_DISABLE_* bit.
     */
    public void setParentKeyguardPolicyEnabled(String key, boolean enabled) {
        if (!("settings_disable_parent_trust_agents".equals(key)
                || "settings_disable_parent_fingerprint".equals(key)
                || "settings_disable_parent_face".equals(key)
                || "settings_disable_parent_iris".equals(key))) {
            throw new IllegalArgumentException("Unknown parent keyguard policy");
        }

        final int policyBit;
        if ("settings_disable_parent_trust_agents".equals(key)) {
            policyBit = DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS;
        } else if ("settings_disable_parent_fingerprint".equals(key)) {
            policyBit = DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT;
        } else if ("settings_disable_parent_face".equals(key)) {
            policyBit = DevicePolicyManager.KEYGUARD_DISABLE_FACE;
        } else if ("settings_disable_parent_iris".equals(key)) {
            policyBit = DevicePolicyManager.KEYGUARD_DISABLE_IRIS;
        } else {
            throw new IllegalArgumentException("Unknown parent keyguard policy");
        }

        Intent intent = new Intent(DummyActivity.SET_PARENT_KEYGUARD_POLICY);
        intent.putExtra("key_name", key);
        intent.putExtra("value", enabled);
        intent.putExtra("policy_key", policyBit);
        intent.putExtra("enabled", enabled);
        intent.putExtra("target_user", Process.myUserHandle().getIdentifier());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Preserve the existing signed cross-profile DummyActivity route.
        // transferIntentToProfile() adds the ordinary timestamp signature;
        // the additional HMAC binds all policy-relevant fields to that time.
        Utility.transferIntentToProfile(mContext, intent);
        AuthenticationUtility.signParentKeyguardPolicyIntent(intent);
        mContext.startActivity(intent);
    }

    // Set the enabled state of the auto freeze service
    // This does NOT need to be synchronized nor applied across profile
    public void setAutoFreezeServiceEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_AUTO_FREEZE_SERVICE, enabled);
    }

    // Get the enabled state of the auto freeze service
    public boolean getAutoFreezeServiceEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_AUTO_FREEZE_SERVICE);
    }

    // Set the delay for auto freeze service (in seconds)
    public void setAutoFreezeDelay(int seconds) {
        mStorage.setInt(LocalStorageManager.PREF_AUTO_FREEZE_DELAY, seconds);
        syncSettingsToProfileInt(LocalStorageManager.PREF_AUTO_FREEZE_DELAY, seconds);
    }

    // Get the delay for auto freeze service
    public int getAutoFreezeDelay() {
        int ret = mStorage.getInt(LocalStorageManager.PREF_AUTO_FREEZE_DELAY);
        if (ret == Integer.MIN_VALUE) {
            // Default delay is 0 seconds
            ret = 0;
        }
        return ret;
    }

    // Set the enabled state of "skip foreground"
    public void setSkipForegroundEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_DONT_FREEZE_FOREGROUND, enabled);
        syncSettingsToProfileBool(LocalStorageManager.PREF_DONT_FREEZE_FOREGROUND, enabled);
    }

    // Get the enabled state of "skip foreground"
    public boolean getSkipForegroundEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_DONT_FREEZE_FOREGROUND);
    }

    public void setWorkToPersonalClipboardEnabled(boolean enabled) {
        mStorage.setBoolean(
                LocalStorageManager.PREF_ALLOW_WORK_TO_PERSONAL_CLIPBOARD,
                enabled
        );
    
        syncSettingsToProfileBool(
                LocalStorageManager.PREF_ALLOW_WORK_TO_PERSONAL_CLIPBOARD,
                enabled
        );
    }
    
    public boolean getWorkToPersonalClipboardEnabled() {
        return mStorage.getBoolean(
                LocalStorageManager.PREF_ALLOW_WORK_TO_PERSONAL_CLIPBOARD
        );
    }

    public void setWorkProfileAppInstallUninstallEnabled(boolean enabled) {
        mStorage.setBoolean(
                LocalStorageManager.PREF_ALLOW_WORK_PROFILE_APP_INSTALL_UNINSTALL,
                enabled
        );
    
        syncSettingsToProfileBool(
                LocalStorageManager.PREF_ALLOW_WORK_PROFILE_APP_INSTALL_UNINSTALL,
                enabled
        );
    }
    
    public boolean getWorkProfileAppInstallUninstallEnabled() {
        return mStorage.getBoolean(
                LocalStorageManager.PREF_ALLOW_WORK_PROFILE_APP_INSTALL_UNINSTALL
        );
    }

    // Allow changing app controls (for example, disable/force-stop) in Work Profile.
    // Defaults to false, so DISALLOW_APPS_CONTROL remains enforced by default.
    public void setWorkProfileAppsControlEnabled(boolean enabled) {
        mStorage.setBoolean(
                LocalStorageManager.PREF_ALLOW_WORK_PROFILE_APPS_CONTROL,
                enabled
        );

        syncSettingsToProfileBool(
                LocalStorageManager.PREF_ALLOW_WORK_PROFILE_APPS_CONTROL,
                enabled
        );
    }

    public boolean getWorkProfileAppsControlEnabled() {
        return mStorage.getBoolean(
                LocalStorageManager.PREF_ALLOW_WORK_PROFILE_APPS_CONTROL
        );
    }

}
