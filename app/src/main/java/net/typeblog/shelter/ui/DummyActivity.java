package net.typeblog.shelter.ui;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.StrictMode;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.core.content.ContextCompat;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.services.FreezeService;
import net.typeblog.shelter.services.IAppInstallCallback;
import net.typeblog.shelter.services.IFileShuttleService;
import net.typeblog.shelter.services.IFileShuttleServiceCallback;
import net.typeblog.shelter.util.AuthenticationUtility;
import net.typeblog.shelter.util.FileProviderProxy;
import net.typeblog.shelter.util.InstallationProgressListener;
import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.SettingsManager;
import net.typeblog.shelter.util.Utility;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

// DummyActivity does nothing about presenting any UI
// It is a wrapper over various different operations
// that might be required to perform across user profiles
// which is only possible through Intents that are in
// the crossProfileIntentFilter
public class DummyActivity extends SecureActivity {
    public static final String FINALIZE_PROVISION = "net.typeblog.shelter.action.FINALIZE_PROVISION";
    public static final String START_SERVICE = "net.typeblog.shelter.action.START_SERVICE";
    public static final String AUTHENTICATE_WORK_PROFILE = "net.typeblog.shelter.action.AUTHENTICATE_WORK_PROFILE";
    public static final String SECURITY_RESPONSE = "net.typeblog.shelter.action.SECURITY_RESPONSE";
    /**
     * SECURITY-CRITICAL:
     *
     * Parent-profile request that permanently applies the configured
     * maximum Work Profile password/security policy. This action MUST
     * remain signature-protected; it must never be added to either
     * unsigned-action allow-list.
     */
    public static final String APPLY_MAXIMUM_WORK_PROFILE_SECURITY =
            "net.typeblog.shelter.action.APPLY_MAXIMUM_WORK_PROFILE_SECURITY";
    public static final String TRY_START_SERVICE = "net.typeblog.shelter.action.TRY_START_SERVICE";
    public static final String INSTALL_PACKAGE = "net.typeblog.shelter.action.INSTALL_PACKAGE";
    public static final String UNINSTALL_PACKAGE = "net.typeblog.shelter.action.UNINSTALL_PACKAGE";
    public static final String UNFREEZE_AND_LAUNCH = "net.typeblog.shelter.action.UNFREEZE_AND_LAUNCH";
    public static final String PUBLIC_FREEZE_ALL = "net.typeblog.shelter.action.PUBLIC_FREEZE_ALL";
    public static final String FREEZE_ALL_IN_LIST = "net.typeblog.shelter.action.FREEZE_ALL_IN_LIST";
    // If we use the same intent for parent -> profile and profile -> parent, the user will
    // be prompted with the action chooser with only one choice in it when the intent is
    // forwarded by Utility.transferIntentToProfile()
    // This is a bad experience, so we use two to avoid this.
    public static final String START_FILE_SHUTTLE = "net.typeblog.shelter.action.START_FILE_SHUTTLE";
    public static final String START_FILE_SHUTTLE_2 = "net.typeblog.shelter.action.START_FILE_SHUTTLE_2";
    public static final String SYNCHRONIZE_PREFERENCE = "net.typeblog.shelter.action.SYNCHRONIZE_PREFERENCE";
    public static final String PACKAGEINSTALLER_CALLBACK = "net.typeblog.shelter.action.PACKAGEINSTALLER_CALLBACK";

    // Only these actions are allowed without a valid signature
    private static final List<String> ACTIONS_ALLOWED_WITHOUT_SIGNATURE = Arrays.asList(
            FINALIZE_PROVISION,
            PUBLIC_FREEZE_ALL);

    // Only these actions are allowed to be called from the same process (pre-registered)
    // without a valid signature
    private static final List<String> ACTIONS_ALLOWED_WITHOUT_SIGNATURE_SAME_PROCESS = Arrays.asList(
            INSTALL_PACKAGE,
            UNINSTALL_PACKAGE,
            UNFREEZE_AND_LAUNCH);

    private static final int REQUEST_INSTALL_PACKAGE = 1;
    private static final int REQUEST_PERMISSION_EXTERNAL_STORAGE= 2;
    private static final int REQUEST_PERMISSION_POST_NOTIFICATIONS = 3;

    private static boolean sHasRequestedPermission = false;

    // A state variable to record the last time DummyActivity was informed that someone
    // in the same process needs to call an action without signature
    // Since they must be in the same process as DummyActivity, it will be totally fine
    // to share a memory state
    private static volatile long sLastSameProcessRequest = -1;

    // Register that an intent will be sent to this Activity without signature
    // from the same process. Each registration is allowed for at most 5 seconds.
    public static synchronized void registerSameProcessRequest(Intent intent) {
        sLastSameProcessRequest = new Date().getTime();
        intent.putExtra("is_same_process", true);
    }

    private static synchronized boolean checkSameProcessRequest(Intent intent) {
        if (!intent.getBooleanExtra("is_same_process", false)) return false;
        if (sLastSameProcessRequest == -1) return false;

        boolean ret = new Date().getTime() - sLastSameProcessRequest <= 5000 // Timeout 5s
                && ACTIONS_ALLOWED_WITHOUT_SIGNATURE_SAME_PROCESS.contains(intent.getAction());
        if (ret) {
            sLastSameProcessRequest = -1; // Revoke the registered request
        }

        return ret;
    }

    private boolean mIsProfileOwner = false;
    private DevicePolicyManager mPolicyManager = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: DummyActivity.onCreate action=" + getIntent().getAction() + ", package=" + getPackageName());

        mPolicyManager = getSystemService(DevicePolicyManager.class);
        mIsProfileOwner = mPolicyManager.isProfileOwnerApp(getPackageName());
        android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: ProfileOwner=" + mIsProfileOwner);
        if (mIsProfileOwner) {
            // If we are the profile owner, enforce all Work Profile policies.
            Utility.enforceWorkProfilePolicies(this);
            Utility.enforceUserRestrictions(this);
            SettingsManager.getInstance().applyAll();

            synchronized (DummyActivity.class) {
                // Do not show permission dialog during finalization -- it will conflict with the provisioning UI
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !sHasRequestedPermission
                        && !FINALIZE_PROVISION.equals(getIntent().getAction())) {
                    // Avoid requesting permission multiple times in one session
                    // This also prevents multiple instances of DummyActivity from being blocked on each other
                    sHasRequestedPermission = true;
                    // We pretty much only send notifications to keep the process inside work profile alive
                    // as such, only request the notification permission from inside the profile
                    // This will ideally be shown and done when the user sees the app list UI for the first time
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_PERMISSION_POST_NOTIFICATIONS);
                        // Continue once the request has been completed (see onRequestPermissionResult)
                        return;
                    }
                }
            }
        }

        init();
    }

    private void init() {
        Intent intent = getIntent();

        // First check if we have a registered request from the same process
        // if it passes, we don't have to check if it has proper signature any more
        if (!checkSameProcessRequest(getIntent())) {
            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: init action=" + intent.getAction() + "; checking cross-profile authentication signature");
            // Check the intent signature first
            // Call checkIntent() first, because we might receive an auth_key from the other side any time.
            // Calling checkIntent() will ensure that the first auth_key is properly received.
            // ONLY the first received one should be stored and trusted.
            if (!AuthenticationUtility.checkIntent(intent)) {
                android.util.Log.e(MAX_SECURITY_LOG_TAG, "WORK: AUTHENTICATION SIGNATURE CHECK FAILED for action=" + intent.getAction());
                // If check failed and not in allowed-without-signature list
                if (!ACTIONS_ALLOWED_WITHOUT_SIGNATURE.contains(intent.getAction())) {
                    // Unauthenticated! Just exit IMMEDIATELY
                    finish();
                    return;
                }
            }
            else {
                android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: AUTHENTICATION SIGNATURE CHECK OK for action=" + intent.getAction());
            }
        }

        if (START_SERVICE.equals(intent.getAction())) {            actionStartService();
        } else if (TRY_START_SERVICE.equals(intent.getAction())) {
            // Dummy activity with dummy intent won't ever fail :)
            // This is used for testing if work mode is disabled from MainActivity
            setResult(RESULT_OK);
            finish();
        } else if (INSTALL_PACKAGE.equals(intent.getAction())) {
            actionInstallPackage();
        } else if (UNINSTALL_PACKAGE.equals(intent.getAction())) {
            actionUninstallPackage();
        } else if (FINALIZE_PROVISION.equals(intent.getAction())) {
            actionFinalizeProvision();
        } else if (UNFREEZE_AND_LAUNCH.equals(intent.getAction())) {
            actionUnfreezeAndLaunch();
        } else if (PUBLIC_FREEZE_ALL.equals(intent.getAction())) {
            actionPublicFreezeAll();
        } else if (FREEZE_ALL_IN_LIST.equals(intent.getAction())) {
            actionFreezeAllInList();
        } else if (START_FILE_SHUTTLE.equals(intent.getAction()) || START_FILE_SHUTTLE_2.equals(intent.getAction())) {
            actionStartFileShuttle();
        } else if (SYNCHRONIZE_PREFERENCE.equals(intent.getAction())) {
            actionSynchronizePreference();
        } else if (SECURITY_RESPONSE.equals(intent.getAction())) {
            actionSecurityResponse();
        } else if (APPLY_MAXIMUM_WORK_PROFILE_SECURITY.equals(intent.getAction())) {
            actionApplyMaximumWorkProfileSecurity();
        } else {
            finish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getAction().equals(PACKAGEINSTALLER_CALLBACK)) {
            int status = intent.getExtras().getInt(PackageInstaller.EXTRA_STATUS);

            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    startActivity((Intent) intent.getExtras().get(Intent.EXTRA_INTENT));
                    break;
                case PackageInstaller.STATUS_SUCCESS:
                    appInstallFinished(Activity.RESULT_OK);
                    break;
                default:
                    appInstallFinished(Activity.RESULT_CANCELED);
                    break;
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_INSTALL_PACKAGE) {
            appInstallFinished(resultCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_EXTERNAL_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                doStartFileShuttle();
            } else {
                finish();
            }
        } else if (requestCode == REQUEST_PERMISSION_POST_NOTIFICATIONS) {
            // Regardless of the result, continue initialization
            // This is fine because most functionalities will work anyway; it will just be a bit buggy
            // and unreliable.
            init();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void actionFinalizeProvision() {
        if (mIsProfileOwner) {
            // Only notify the main profile on pre-Oreo
            // After Oreo, since we use the activity-based finalization flow,
            // the setup wizard will wait until we finish finalization before returning
            // (Note: the actual finalization is done by common code in onCreate)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // This is the action used by DeviceAdminReceiver to finalize the setup
                // The work has been finished in onCreate(), now we just have to
                // inform the main profile about this
                Intent intent = new Intent(FINALIZE_PROVISION);
                // We don't need signature for this intent
                Utility.transferIntentToProfileUnsigned(this, intent);
                startActivity(intent);
            }
            finish();
        } else {
            // Set the flag telling MainActivity that we have now finished provisioning
            LocalStorageManager.getInstance()
                    .setBoolean(LocalStorageManager.PREF_HAS_SETUP, true);
            LocalStorageManager.getInstance()
                    .setBoolean(LocalStorageManager.PREF_IS_SETTING_UP, false);
            Intent intent = new Intent(SetupWizardActivity.ACTION_PROFILE_PROVISIONED);
            intent.setComponent(new ComponentName(this, SetupWizardActivity.class));
            startActivity(intent);
            Toast.makeText(this, getString(R.string.provision_finished), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * Authentication UI for the Work Profile. This Activity is exported only
     * so Android's cross-profile intent forwarding can resolve it; the caller
     * is still authenticated with Shelter's existing signed Intent mechanism.
     */
    public static class WorkProfileAuthenticationActivity extends SecureActivity {
        private static final String TAG = "ShelterWorkProfileAuth";

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            if (!AuthenticationUtility.checkIntent(getIntent())) {
                setResult(Activity.RESULT_CANCELED);
                finish();
                return;
            }

            if (savedInstanceState == null) {
                getSupportFragmentManager().beginTransaction()
                        .add(new AuthenticationFragment(), TAG)
                        .commit();
            }
        }

        public static class AuthenticationFragment extends Fragment {
            private static final String STATE_STARTED = "authentication_started";
            private boolean mStarted;

            @Override
            public void onCreate(@Nullable Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setRetainInstance(true);
                mStarted = savedInstanceState != null
                        && savedInstanceState.getBoolean(STATE_STARTED, false);
            }

            @Override
            public void onSaveInstanceState(@NonNull Bundle outState) {
                outState.putBoolean(STATE_STARTED, mStarted);
                super.onSaveInstanceState(outState);
            }

            @Override
            public void onResume() {
                super.onResume();
                if (!mStarted) {
                    mStarted = true;
                    startAuthentication();
                }
            }

            private void finishAuthentication(int resultCode) {
                Activity activity = getActivity();
                if (activity != null && !activity.isFinishing()) {
                    activity.setResult(resultCode);
                    activity.finish();
                }
            }

            private void startAuthentication() {
                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    finishAuthentication(Activity.RESULT_CANCELED);
                    return;
                }

                BiometricManager manager =
                        activity.getSystemService(BiometricManager.class);
                if (manager == null) {
                    finishAuthentication(Activity.RESULT_CANCELED);
                    return;
                }

                final int authenticators =
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL;

                int availability = manager.canAuthenticate(authenticators);

                // No PIN/pattern/password exists for this profile.
                // The requested policy treats this as successful authentication.
                if (availability == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL) {
                    finishAuthentication(Activity.RESULT_CANCELED);
                    return;
                }

                if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
                    finishAuthentication(Activity.RESULT_CANCELED);
                    return;
                }

                try {
                    BiometricPrompt prompt = new BiometricPrompt.Builder(activity)
                            .setTitle(getString(R.string.app_name))
                            .setSubtitle("Unlock Work Profile")
                            .setDescription("Enter your Work Profile PIN, pattern, or password.")
                            .setAllowedAuthenticators(authenticators)
                            .build();

                    prompt.authenticate(
                            new CancellationSignal(),
                            activity.getMainExecutor(),
                            new BiometricPrompt.AuthenticationCallback() {
                                @Override
                                public void onAuthenticationSucceeded(
                                        BiometricPrompt.AuthenticationResult result) {
                                    if (result.getAuthenticationType() ==
                                            BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL) {
                                        finishAuthentication(Activity.RESULT_OK);
                                    } else {
                                        finishAuthentication(Activity.RESULT_CANCELED);
                                    }
                                }

                                @Override
                                public void onAuthenticationError(
                                        int errorCode, CharSequence errString) {
                                    finishAuthentication(Activity.RESULT_CANCELED);
                                }
                            });
                } catch (RuntimeException e) {
                    finishAuthentication(Activity.RESULT_CANCELED);
                }
            }
        }
    }

    private void actionStartService() {
        // This needs to be foreground because this activity won't be able to hold
        // the ServiceConnection to it.
        ((ShelterApplication) getApplication()).bindShelterService(new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Intent data = new Intent();
                Bundle bundle = new Bundle();
                bundle.putBinder("service", service);
                data.putExtra("extra", bundle);
                setResult(RESULT_OK, data);
                finish();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // dummy
            }
        }, true);
    }

    private void actionInstallPackage() {
        Uri uri = null;
        if (getIntent().hasExtra("package")) {
            uri = Uri.fromParts("package", getIntent().getStringExtra("package"), null);
        }
        StrictMode.VmPolicy policy = StrictMode.getVmPolicy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || getIntent().hasExtra("direct_install_apk")) {
            if (getIntent().hasExtra("apk")) {
                // I really have no idea about why the "package:" uri do not work
                // after Android O, anyway we fall back to using the apk path...
                // Since I have plan to support pre-O in later versions, I keep this
                // branch in case that we reduce minSDK in the future.
                uri = Uri.fromFile(new File(getIntent().getStringExtra("apk")));
            } else if (getIntent().hasExtra("direct_install_apk")) {
                // Directly install an APK inside the profile
                // The APK will be an Uri from our own FileProviderProxy
                // which points to an opened Fd in another profile.
                // We must close the Fd when we finish.
                uri = getIntent().getParcelableExtra("direct_install_apk");
            }

            // A permissive VmPolicy must be set to work around
            // the limitation on cross-application Uri
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        
            try {
                actionInstallPackageQ(
                        uri,
                        getIntent().getStringArrayExtra("split_apks")
                );
            } catch (Exception e) {

                appInstallFinished(
                        RESULT_CANCELED
                );

            }

        } else {

            Intent intent =
                    new Intent(
                            Intent.ACTION_INSTALL_PACKAGE,
                            uri
                    );

            intent.putExtra(
                    Intent.EXTRA_INSTALLER_PACKAGE_NAME,
                    getPackageName()
            );

            intent.putExtra(
                    Intent.EXTRA_NOT_UNKNOWN_SOURCE,
                    true
            );

            intent.putExtra(
                    Intent.EXTRA_RETURN_RESULT,
                    true
            );

            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            try {

                startActivityForResult(
                        intent,
                        REQUEST_INSTALL_PACKAGE
                );

            } catch (Exception e) {

                setResult(
                        RESULT_CANCELED
                );

                finish();
        
            }
        }

        // Restore the VmPolicy anyway
        StrictMode.setVmPolicy(policy);
    }

    // On Android Q, ACTION_INSTALL_PACKAGE has been deprecated.
    // We have to switch to using PackageInstaller for the job, which isn't quite
    // as elegant because now we really need to read the entire apk and write to it
    // Keep this case only for Q for now.
    private void actionInstallPackageQ(Uri uri, String[] split_apks) throws IOException {
        PackageInstaller pi = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId = pi.createSession(params);

        // Show the progress dialog first
        pi.registerSessionCallback(new InstallationProgressListener(this, pi, sessionId));

        PackageInstaller.Session session = pi.openSession(sessionId);
        doInstallPackageQ(uri, split_apks, session, () -> {
            // We have finished piping the streams, show the progress as 10%
            session.setStagingProgress(0.1f);

            // Commit the session
            Intent intent = new Intent(this, DummyActivity.class);
            intent.setAction(PACKAGEINSTALLER_CALLBACK);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    intent, PendingIntent.FLAG_MUTABLE);
            session.commit(pendingIntent.getIntentSender());
        });
    }

    // The background part of the installation process on Q (reading APKs etc)
    // that must be executed on another thread
    // Put them in background to avoid stalling the UI thread
    private void doInstallPackageQ(Uri baseUri, String[] split_apks, PackageInstaller.Session session, Runnable callback) {
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(baseUri);
        if (split_apks != null && split_apks.length > 0) {
            for (String apk : split_apks) {
                uris.add(Uri.fromFile(new File(apk)));
            }
        }

        new Thread(() -> {
            for (Uri uri : uris) {
                try (
                        InputStream is =
                                getContentResolver()
                                        .openInputStream(uri)
                ) {
                
                    if (is == null) {
                
                        throw new IOException(
                                "Cannot open APK stream"
                        );
                    }
                
                
                    try (
                        OutputStream os =
                                session.openWrite(
                                        UUID.randomUUID().toString(),
                                        0,
                                        is.available()
                                )
                    ) {
                
                        Utility.pipe(is, os);
                
                        session.fsync(os);
                    }
                
                } catch (Exception e) {
                
                    session.abandon();
                
                }
            }

            runOnUiThread(callback);
        }).start();
    }

    private void actionUninstallPackage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            actionUninstallPackageQ();
            return;
        }

        Uri uri = Uri.fromParts("package", getIntent().getStringExtra("package"), null);
        Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri);
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        // Currently, Install & Uninstall share the same logic
        // after starting the system PackageInstaller
        // because the only thing to do is to call the callback
        // with the result code.
        // If ANY separate logic is added for any of them,
        // the request code should be separated.
        try {

            startActivityForResult(
                    intent,
                    REQUEST_INSTALL_PACKAGE
            );
        
        
        } catch (Exception e) {
        
        
            setResult(
                    RESULT_CANCELED
            );
        
            finish();
        }
    }

    private void actionUninstallPackageQ() {

        try {
            PackageInstaller pi =
                    getPackageManager()
                            .getPackageInstaller();
            Intent intent =
                    new Intent(
                            this,
                            DummyActivity.class
                    );

            intent.setAction(
                    PACKAGEINSTALLER_CALLBACK
            );

            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            this,
                            0,
                            intent,
                            PendingIntent.FLAG_MUTABLE
                    );

            pi.uninstall(
                    getIntent()
                            .getStringExtra("package"),
                    pendingIntent.getIntentSender()
            );

        } catch (Exception e) {

            appInstallFinished(
                    RESULT_CANCELED
            );
    
        }
    }

    private void appInstallFinished(int resultCode) {
        // Clear the fd anyway since we have finished installation.
        // Because we might have been installing an APK opened from
        // the other profile. We don't know, but just clean it.
        FileProviderProxy.clearForwardProxy();

        if (!getIntent().hasExtra("callback")) return;

        // Send the result code back to the caller
        Bundle callbackExtra = getIntent().getBundleExtra("callback");
        IAppInstallCallback callback = IAppInstallCallback.Stub
                .asInterface(callbackExtra.getBinder("callback"));

        try {
            callback.callback(resultCode);
        } catch (RemoteException e) {
            // do nothing
        }

        finish();
    }

    private void actionUnfreezeAndLaunch() {
        // Unfreeze and launch an app
        // (actually this also works if the app is not frozen at all)
        // For now we only support apps in Work profile,
        // so we just check if we are profile owner here
        if (!mIsProfileOwner) {
            // Forward it to work profile
            Intent intent = new Intent(UNFREEZE_AND_LAUNCH);
            Utility.transferIntentToProfile(this, intent);
            String packageName = getIntent().getStringExtra("packageName");
            intent.putExtra("packageName", packageName);
            intent.putExtra("shouldFreeze",
                    SettingsManager.getInstance().getAutoFreezeServiceEnabled() &&
                            LocalStorageManager.getInstance()
                                .stringListContains(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, packageName));
            if (getIntent().hasExtra("linkedPackages")) {
                // Multiple apps should be unfrozen here
                String[] packages = getIntent().getStringExtra("linkedPackages").split(",");
                boolean[] packagesShouldFreeze = new boolean[packages.length];

                for (int i = 0; i < packages.length; i++) {
                    // Apps in linkedPackages may also need to be auto-frozen
                    // thus, we loop through them and fetch the settings
                    packagesShouldFreeze[i] = SettingsManager.getInstance().getAutoFreezeServiceEnabled() &&
                            LocalStorageManager.getInstance()
                                    .stringListContains(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, packages[i]);
                }
                intent.putExtra("linkedPackages", packages);
                intent.putExtra("linkedPackagesShouldFreeze", packagesShouldFreeze);
            }
            startActivity(intent);
            finish();
            return;
        }

        // If we have multiple linked apps to unfreeze before launching the main one
        if (getIntent().hasExtra("linkedPackages")) {
            String[] packages = getIntent().getStringArrayExtra("linkedPackages");
            boolean[] packagesShouldFreeze = getIntent().getBooleanArrayExtra("linkedPackagesShouldFreeze");

            for (int i = 0; i < packages.length; i++) {
                // Unfreeze everything
                mPolicyManager.setApplicationHidden(
                        new ComponentName(this, ShelterDeviceAdminReceiver.class),
                        packages[i], false);
                // Register freeze service
                if (packagesShouldFreeze[i]) {
                    registerAppToFreeze(packages[i]);
                }
            }
        }

        // Here is the main package to launch
        String packageName = getIntent().getStringExtra("packageName");

        // Unfreeze the app first
        mPolicyManager.setApplicationHidden(
                new ComponentName(this, ShelterDeviceAdminReceiver.class),
                packageName, false);

        // Query the start intent
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);

        if (launchIntent != null) {
            if (getIntent().getBooleanExtra("shouldFreeze", false)) {
                registerAppToFreeze(packageName);
            }
            startActivity(launchIntent);
        } else {
            // Acknowledge the user that the application cannot be launched
            Toast.makeText(this, getString(R.string.launch_app_fail, packageName), Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    private void registerAppToFreeze(String packageName) {
        FreezeService.registerAppToFreeze(packageName);
        startService(new Intent(this, FreezeService.class));
    }

    private void actionPublicFreezeAll() {
        // For now we only support freezing apps in work profile
        // so forward this to DummyActivity in work profile
        // after loading the full list to freeze
        if (!mIsProfileOwner) {
            Intent intent = new Intent(FREEZE_ALL_IN_LIST);
            Utility.transferIntentToProfile(this, intent);
            String[] list = LocalStorageManager.getInstance()
                    .getStringList(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE);
            intent.putExtra("list", list);
            startActivity(intent);
            finish();
        } else {
            throw new RuntimeException("unimplemented");
        }
    }

    private void actionFreezeAllInList() {
        if (mIsProfileOwner) {
            String[] list = getIntent().getStringArrayExtra("list");
            for (String pkg : list) {
                mPolicyManager.setApplicationHidden(
                        new ComponentName(this, ShelterDeviceAdminReceiver.class),
                        pkg, true);
            }
            stopService(new Intent(this, FreezeService.class)); // Stop the auto-freeze service
            Toast.makeText(this, R.string.freeze_all_success, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            finish();
        }
    }

    private void actionStartFileShuttle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // This requires the permission WRITE_EXTERNAL_STORAGE
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                doStartFileShuttle();
            } else {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_EXTERNAL_STORAGE);
            }
        } else {
            // The all file access permission should have been granted when enabling File Shuttle
            // since Android R.
            if (Utility.checkAllFileAccessPermission() && Utility.checkSystemAlertPermission(this)) {
                doStartFileShuttle();
            } else {
                finish();
            }
        }
    }

    private void doStartFileShuttle() {
        ((ShelterApplication) getApplication()).bindFileShuttleService(new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                IFileShuttleService shuttle = IFileShuttleService.Stub.asInterface(service);
                IFileShuttleServiceCallback callback = IFileShuttleServiceCallback.Stub.asInterface(
                        getIntent().getBundleExtra("extra").getBinder("callback"));
                try {
                    callback.callback(shuttle);
                } catch (RemoteException e) {
                    // Do Nothing
                }

                finish();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // Do Nothing
            }
        });
    }

    private void actionSynchronizePreference() {
        String name = getIntent().getStringExtra("name");
        if (getIntent().hasExtra("boolean")) {
            LocalStorageManager.getInstance()
                    .setBoolean(name, getIntent().getBooleanExtra("boolean", false));
        } else if (getIntent().hasExtra("int")) {
            LocalStorageManager.getInstance()
                    .setInt(name, getIntent().getIntExtra("int", Integer.MIN_VALUE));
        }
        // TODO: Cases for other types
        SettingsManager.getInstance().applyAll();
        if (mIsProfileOwner) {
            // Refresh profile policies because
            // settings may have been changed
            Utility.enforceWorkProfilePolicies(this);
            Utility.enforceUserRestrictions(this);
        }
        finish();
    }

    /*
     * ============================================================
     * PERMANENT MAXIMUM WORK PROFILE SECURITY POLICY
     * ============================================================
     *
     * SECURITY-CRITICAL:
     *
     * This operation is intentionally one-way from Shelter's point
     * of view. There is no matching "remove" or "disable" action.
     *
     * The request has already passed AuthenticationUtility.checkIntent()
     * in init(). We additionally require this process to be the Work
     * Profile owner before touching DevicePolicyManager.
     *
     * The permanent latch is written ONLY after every requested policy
     * has been applied and read back successfully.
     *
     * IMPORTANT:
     *
     * Android's PASSWORD_QUALITY_COMPLEX and setPasswordMinimum* APIs
     * are deprecated since API 31, but they are intentionally used here
     * because this Shelter policy requires exact character-class
     * requirements. The project targets API 35. The APIs remain part of
     * the Android 16 framework and are guarded by PASSWORD_QUALITY_COMPLEX
     * before the minimum-character setters are called.
     *
     * DO NOT replace this with PASSWORD_COMPLEXITY_HIGH: that platform
     * complexity API does not express the exact requirements below.
     */
    @SuppressWarnings("deprecation")
    private void actionApplyMaximumWorkProfileSecurity() {

        android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: actionApplyMaximumWorkProfileSecurity ENTERED");

        if (!mIsProfileOwner) {
            android.util.Log.e(MAX_SECURITY_LOG_TAG, "WORK: ABORT - not Profile Owner");
            finish();
            return;
        }

        final ComponentName admin =
                new ComponentName(
                        this,
                        ShelterDeviceAdminReceiver.class
                );

        if (mPolicyManager == null ||
                !mPolicyManager.isProfileOwnerApp(getPackageName())) {
            android.util.Log.e(MAX_SECURITY_LOG_TAG, "WORK: ABORT - DPM null or Profile Owner recheck failed");
            finish();
            return;
        }

        /*
         * SECURITY-CRITICAL:
         *
         * Idempotence / one-way latch.
         *
         * Once the complete policy has been successfully applied and
         * verified, every later request is a harmless no-op. There is
         * deliberately no code path that reverses these policies.
         */
        if (LocalStorageManager.getInstance().getBoolean(
                LocalStorageManager.PREF_MAXIMUM_WORK_PROFILE_SECURITY_APPLIED)) {
            android.util.Log.w(MAX_SECURITY_LOG_TAG, "WORK: NO-OP - permanent latch already set");
            finish();
            return;
        }

        /*
         * The requested password length is 65 characters.
         *
         * getPasswordMaximumLength() is a capability check, not a
         * policy read-back. If the device cannot represent a 65-character
         * password for PASSWORD_QUALITY_COMPLEX, do not mark the policy
         * as permanently applied.
         */
        final int requiredMinimumLength = 65;
        final int maximumSupportedLength;
        try {
            maximumSupportedLength =
                    mPolicyManager.getPasswordMaximumLength(
                            DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
                    );
            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: getPasswordMaximumLength(COMPLEX)=" + maximumSupportedLength + "; required=65");
        } catch (RuntimeException e) {
            android.util.Log.e(MAX_SECURITY_LOG_TAG, "WORK: ABORT - getPasswordMaximumLength() threw an exception", e);
            finish();
            return;
        }

        if (maximumSupportedLength < requiredMinimumLength) {
            android.util.Log.e(
                    "ShelterMaxSecurity",
                    "Device cannot support required 65-character Work Profile password. " +
                            "Maximum supported length=" + maximumSupportedLength
            );
            finish();
            return;
        }

        /*
         * A history length of 1 means the immediately previous password
         * cannot be reused. This directly implements the requirement that
         * the previous Work Profile password must not be reused.
         */
        final int passwordHistoryLength = 1;

        final long maximumTimeToLock =
                20L * 60L * 1000L; // 20 minutes

        final long passwordExpirationTimeout =
                30L * 24L * 60L * 60L * 1000L; // 30 days

        try {
            /*
             * PASSWORD_QUALITY_COMPLEX MUST be set FIRST.
             *
             * On apps targeting Android R or newer, the
             * setPasswordMinimum* methods require this quality to have
             * been selected first.
             */
            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: applying setPasswordQuality(COMPLEX)");
            mPolicyManager.setPasswordQuality(
                    admin,
                    DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            );
            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: setPasswordQuality OK");

            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: setPasswordMinimumLength=" + requiredMinimumLength);
            mPolicyManager.setPasswordMinimumLength(
                    admin,
                    requiredMinimumLength
            );

            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: setPasswordMinimumLetters=10");
            mPolicyManager.setPasswordMinimumLetters(
                    admin,
                    10
            );

            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: setPasswordMinimumUpperCase=5");
            mPolicyManager.setPasswordMinimumUpperCase(
                    admin,
                    5
            );

            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: setPasswordMinimumLowerCase=5");
            mPolicyManager.setPasswordMinimumLowerCase(
                    admin,
                    5
            );

            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: setPasswordMinimumNumeric=10");
            mPolicyManager.setPasswordMinimumNumeric(
                    admin,
                    10
            );

            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: setPasswordMinimumSymbols=10");
            mPolicyManager.setPasswordMinimumSymbols(
                    admin,
                    10
            );

            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: setPasswordMinimumNonLetter=20");
            mPolicyManager.setPasswordMinimumNonLetter(
                    admin,
                    20
            );

            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: setMaximumFailedPasswordsForWipe=5");
            mPolicyManager.setMaximumFailedPasswordsForWipe(
                    admin,
                    5
            );

            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: setPasswordHistoryLength=1");
            mPolicyManager.setPasswordHistoryLength(
                    admin,
                    passwordHistoryLength
            );

            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: setMaximumTimeToLock=" + maximumTimeToLock);
            mPolicyManager.setMaximumTimeToLock(
                    admin,
                    maximumTimeToLock
            );

            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: setPasswordExpirationTimeout=" + passwordExpirationTimeout);
            mPolicyManager.setPasswordExpirationTimeout(
                    admin,
                    passwordExpirationTimeout
            );

            /*
             * Require a separate Work Profile challenge.
             */
            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: addUserRestriction(DISALLOW_UNIFIED_PASSWORD)");
            mPolicyManager.addUserRestriction(
                    admin,
                    UserManager.DISALLOW_UNIFIED_PASSWORD
            );
            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: all policy setters returned successfully");

        } catch (RuntimeException e) {

            /*
             * SECURITY:
             *
             * Do NOT set the permanent latch if any policy operation
             * fails. Some earlier setters may already have succeeded;
             * there is intentionally no "undo" operation because this
             * feature is one-way. A later signed request may retry the
             * remaining configuration.
             */
            android.util.Log.e(
                    MAX_SECURITY_LOG_TAG,
                    "WORK: FAILED while applying maximum Work Profile security policy",
                    e
            );

            finish();
            return;
        }

        /*
         * ============================================================
         * READ-BACK VERIFICATION
         * ============================================================
         *
         * Do not trust successful setter calls alone. Every policy for
         * which Android exposes a direct read-back API is verified here.
         */
        try {

            boolean verified =
                    mPolicyManager.getPasswordQuality(admin)
                            == DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
                    && mPolicyManager.getPasswordMinimumLength(admin)
                            == requiredMinimumLength
                    && mPolicyManager.getPasswordMinimumLetters(admin)
                            == 10
                    && mPolicyManager.getPasswordMinimumUpperCase(admin)
                            == 5
                    && mPolicyManager.getPasswordMinimumLowerCase(admin)
                            == 5
                    && mPolicyManager.getPasswordMinimumNumeric(admin)
                            == 10
                    && mPolicyManager.getPasswordMinimumSymbols(admin)
                            == 10
                    && mPolicyManager.getPasswordMinimumNonLetter(admin)
                            == 20
                    && mPolicyManager.getMaximumFailedPasswordsForWipe(admin)
                            == 5
                    && mPolicyManager.getPasswordHistoryLength(admin)
                            == passwordHistoryLength
                    && mPolicyManager.getMaximumTimeToLock(admin)
                            == maximumTimeToLock
                    && mPolicyManager.getPasswordExpirationTimeout(admin)
                            == passwordExpirationTimeout;

            /*
             * Verify the restriction was actually installed by this
             * profile owner.
             */
            Bundle restrictions =
                    mPolicyManager.getUserRestrictions(admin);

            android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: restriction DISALLOW_UNIFIED_PASSWORD=" + restrictions.getBoolean(UserManager.DISALLOW_UNIFIED_PASSWORD, false));

            verified = verified &&
                    restrictions.getBoolean(
                            UserManager.DISALLOW_UNIFIED_PASSWORD,
                            false
                    );

            /*
             * IMPORTANT:
             *
             * isUsingUnifiedPassword() is a resulting profile state,
             * not the read-back value of the restriction itself.
             *
             * DISALLOW_UNIFIED_PASSWORD can be successfully installed
             * while Android still needs the user to complete the
             * separate Work Profile password enrollment. Therefore we
             * verify the restriction here, then handle the resulting
             * credential-enrollment state below.
             */

            if (!verified) {

                android.util.Log.e(
                        MAX_SECURITY_LOG_TAG,
                        "Maximum Work Profile security policy read-back verification failed"
                );

                /*
                 * Never set the permanent latch after incomplete
                 * verification.
                 */
                finish();
                return;
            }

        } catch (RuntimeException e) {

            android.util.Log.e(
                    MAX_SECURITY_LOG_TAG,
                    "WORK: exception while verifying maximum Work Profile security policy",
                    e
            );

            finish();
            return;
        }

        /*
         * ============================================================
         * PERMANENT LATCH
         * ============================================================
         *
         * This is deliberately the LAST operation.
         *
         * Once true, a later APPLY request is a no-op and there is no
         * Shelter code path that clears this flag or reverses the
         * security policy.
         */
        LocalStorageManager.getInstance().setBoolean(
                LocalStorageManager.PREF_MAXIMUM_WORK_PROFILE_SECURITY_APPLIED,
                true
        );

        android.util.Log.i(MAX_SECURITY_LOG_TAG, "WORK: SUCCESS - all policies verified; permanent latch SET");

        /*
         * The policy may be active while the existing password is still
         * too weak. Android deliberately does not replace the current
         * password when a minimum policy is tightened.
         *
         * If the current Work Profile credential is insufficient, launch
         * the system password-change flow. The policy itself remains
         * permanently active even if the user cancels that flow.
         */
        try {
            /*
             * If Android still reports a unified challenge, the user must
             * complete the separate Work Profile credential enrollment.
             */
            if (mPolicyManager.isUsingUnifiedPassword(admin) ||
                    !mPolicyManager.isActivePasswordSufficient()) {

                Intent setNewPassword =
                        new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
                setNewPassword.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(setNewPassword);
            }
        } catch (RuntimeException e) {
            /*
             * The permanent policy is already committed. Failure to open
             * the optional password-change UI must NOT roll the policy back.
             */
            android.util.Log.e(
                    "ShelterMaxSecurity",
                    "Maximum policy applied, but current password could not be evaluated/changed",
                    e
            );
        }

        finish();
    }

    private void actionSecurityResponse() {
    
        if (!mIsProfileOwner) {
            finish();
            return;
        }
    
        ComponentName admin =
                new ComponentName(
                        this,
                        ShelterDeviceAdminReceiver.class
                );
    
        try {
            DevicePolicyManager parentManager =
                    mPolicyManager.getParentProfileInstance(admin);
    
            int keyguardFlags =
                    DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS
                    | DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT
                    | DevicePolicyManager.KEYGUARD_DISABLE_FACE
                    | DevicePolicyManager.KEYGUARD_DISABLE_IRIS;
    
            try {
                parentManager.setKeyguardDisabledFeatures(
                        admin,
                        keyguardFlags
                );
    
                android.util.Log.i(
                        "ShelterSecurityResponse",
                        "Parent Keyguard restrictions applied"
                );
    
            } catch (SecurityException e) {
    
                android.util.Log.e(
                        "ShelterSecurityResponse",
                        "Failed to apply Parent Keyguard restrictions",
                        e
                );
            }

        } catch (SecurityException e) {
    
            android.util.Log.e(
                    "ShelterSecurityResponse",
                    "Cannot obtain Parent Profile DPM",
                    e
            );
        }
    
        finish();
    }
}

