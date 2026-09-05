package net.typeblog.shelter.ui;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.services.IAppInstallCallback;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.services.IStartActivityProxy;
import net.typeblog.shelter.services.KillerService;
import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.SettingsManager;
import net.typeblog.shelter.util.UriForwardProxy;
import net.typeblog.shelter.util.Utility;

public class MainActivity extends SecureActivity {
    public static final String BROADCAST_CONTEXT_MENU_CLOSED = "net.typeblog.shelter.broadcast.CONTEXT_MENU_CLOSED";
    public static final String BROADCAST_SEARCH_FILTER_CHANGED = "net.typeblog.shelter.broadcast.SEARCH_FILTER_CHANGED";

    private final ActivityResultLauncher<Void> mStartSetup =
            registerForActivityResult(new SetupWizardActivity.SetupWizardContract(), this::setupWizardCb);
    private final ActivityResultLauncher<Void> mResumeSetup =
            registerForActivityResult(new SetupWizardActivity.ResumeSetupContract(), this::setupWizardCb);
    private final ActivityResultLauncher<Void> mSelectApk =
            registerForActivityResult(
                    new Utility.ActivityResultContractInputWrapper<>(
                            new ActivityResultContracts.OpenDocument(),
                            new String[]{"application/vnd.android.package-archive"}),
                    this::onApkSelected);

    private final ActivityResultLauncher<Intent> mTryStartWorkService =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::tryStartWorkServiceCb);
    private final ActivityResultLauncher<Intent> mAuthenticateWorkProfile =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::authenticateWorkProfileCb);
    private final ActivityResultLauncher<Intent> mBindWorkService =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::bindWorkServiceCb);

    private LocalStorageManager mStorage = null;

    private boolean mRestarting = false;
    private boolean mWorkAuthenticationInProgress = false;
    private boolean mWorkAuthenticationSucceeded = false;
    private boolean mStartupInProgress = false;

    private IShelterService mServiceMain = null;
    private IShelterService mServiceWork = null;

    boolean mShowAll = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.main_toolbar));
        mStorage = LocalStorageManager.getInstance();

        if (savedInstanceState != null) {
            mWorkAuthenticationInProgress =
                    savedInstanceState.getBoolean("work_auth_in_progress", false);
            mWorkAuthenticationSucceeded =
                    savedInstanceState.getBoolean("work_auth_succeeded", false);
            mStartupInProgress =
                    savedInstanceState.getBoolean("startup_in_progress", false);
        }

        if (getSystemService(DevicePolicyManager.class).isProfileOwnerApp(getPackageName())) {
            android.util.Log.d("MainActivity", "started in user profile. stopping.");
            finish();
        } else {
            init();
        }
    }

    private void init() {
        if (mStorage.getBoolean(LocalStorageManager.PREF_IS_SETTING_UP) && !Utility.isWorkProfileAvailable(this)) {
            mResumeSetup.launch(null);
        } else if (!mStorage.getBoolean(LocalStorageManager.PREF_HAS_SETUP)) {
            mStartSetup.launch(null);
        } else {
            SettingsManager.getInstance().applyAll();
            bindServices();
        }
    }

    private void setupWizardCb(Boolean result) {
        if (result)
            init();
        else
            finish();
    }

    private void bindServices() {
        if (mStartupInProgress || mServiceMain != null) {
            return;
        }
        mStartupInProgress = true;

        ((ShelterApplication) getApplication()).bindShelterService(new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceMain = IShelterService.Stub.asInterface(service);
                tryStartWorkService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // dummy
            }
        }, false);
    }

    private void tryStartWorkService() {
        Intent intent = new Intent(DummyActivity.TRY_START_SERVICE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            Utility.transferIntentToProfile(this, intent);
        } catch (IllegalStateException e) {
            mStorage.setBoolean(LocalStorageManager.PREF_HAS_SETUP, false);
            Toast.makeText(this, getString(R.string.work_profile_not_found), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        mTryStartWorkService.launch(intent);
    }

    private void tryStartWorkServiceCb(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            // Work Profile is enabled. Authentication is mandatory before
            // the Work Profile ShelterService can be bound.
            authenticateWorkProfile();
        } else {
            Toast.makeText(this,
                    getString(R.string.work_mode_disabled), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void authenticateWorkProfile() {
        if (mWorkAuthenticationInProgress || mWorkAuthenticationSucceeded || mServiceWork != null) {
            return;
        }

        Intent intent = new Intent(DummyActivity.AUTHENTICATE_WORK_PROFILE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

        try {
            Utility.transferIntentToProfile(this, intent);
        } catch (IllegalStateException e) {
            Toast.makeText(this,
                    getString(R.string.work_profile_not_found), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mWorkAuthenticationInProgress = true;
        mAuthenticateWorkProfile.launch(intent);
    }

    private void authenticateWorkProfileCb(ActivityResult result) {
        mWorkAuthenticationInProgress = false;

        if (result.getResultCode() == RESULT_OK) {
            mWorkAuthenticationSucceeded = true;
            bindWorkService();
        } else {
            mWorkAuthenticationSucceeded = false;
            Toast.makeText(this,
                    getString(R.string.work_mode_disabled), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void bindWorkService() {
        if (!mWorkAuthenticationSucceeded || mServiceWork != null) {
            return;
        }

        Intent intent = new Intent(DummyActivity.START_SERVICE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        Utility.transferIntentToProfile(this, intent);
        mBindWorkService.launch(intent);
    }

    private void bindWorkServiceCb(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Bundle extra = result.getData().getBundleExtra("extra");
            if (extra == null) {
                finish();
                return;
            }
            IBinder binder = extra.getBinder("service");
            if (binder == null) {
                finish();
                return;
            }
            mServiceWork = IShelterService.Stub.asInterface(binder);
            mStartupInProgress = false;
            registerStartActivityProxies();
            startKiller();
            buildView();
        } else {
            mStartupInProgress = false;
            finish();
        }
    }

    private void startKiller() {
        Intent intent = new Intent(this, KillerService.class);
        Bundle bundle = new Bundle();
        bundle.putBinder("main", mServiceMain.asBinder());
        bundle.putBinder("work", mServiceWork.asBinder());
        intent.putExtra("extra", bundle);
        startService(intent);
    }

    private void buildView() {
        ViewPager2 pager = findViewById(R.id.main_pager);
        BottomNavigationView nav = findViewById(R.id.main_bottom_navigation);

        pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return AppListFragment.newInstance(mServiceMain, false);
                } else if (position == 1) {
                    return AppListFragment.newInstance(mServiceWork, true);
                } else {
                    throw new RuntimeException("How did this happen?");
                }
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                int[] menuIds = new int[]{
                        R.id.bottom_navigation_main,
                        R.id.bottom_navigation_work
                };
                nav.setSelectedItemId(menuIds[position]);
            }
        });
        nav.setOnItemSelectedListener((MenuItem item) -> {
            int itemId = item.getItemId();
            if (itemId == R.id.bottom_navigation_main) {
                pager.setCurrentItem(0);
            } else if (itemId == R.id.bottom_navigation_work) {
                pager.setCurrentItem(1);
            }
            return true;
        });
    }

    IShelterService getOtherService(boolean isRemote) {
        return isRemote ? mServiceMain : mServiceWork;
    }

    boolean servicesAlive() {
        try {
            mServiceMain.ping();
        } catch (Exception e) {
            return false;
        }
        try {
            mServiceWork.ping();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private void registerStartActivityProxies() {
        try {
            mServiceMain.setStartActivityProxy(new IStartActivityProxy.Stub() {
                @Override
                public void startActivity(Intent intent) throws RemoteException {
                    MainActivity.this.startActivity(intent);
                }
            });
            mServiceWork.setStartActivityProxy(new IStartActivityProxy.Stub() {
                @Override
                public void startActivity(Intent intent) throws RemoteException {
                    Intent dummyIntent = new Intent(intent.getAction());
                    Utility.transferIntentToProfileUnsigned(MainActivity.this, dummyIntent);
                    intent.setComponent(dummyIntent.getComponent());
                    MainActivity.this.startActivity(intent);
                }
            });
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("work_auth_in_progress", mWorkAuthenticationInProgress);
        outState.putBoolean("work_auth_succeeded", mWorkAuthenticationSucceeded);
        outState.putBoolean("startup_in_progress", mStartupInProgress);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mServiceMain != null && mServiceWork != null && !servicesAlive()) {
            doOnDestroy();
            mRestarting = true;
            Intent intent = getIntent();
            finish();
            startActivity(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!mRestarting)
            doOnDestroy();
    }

    private void doOnDestroy() {
        stopService(new Intent(this, KillerService.class));
        Utility.killShelterServices(mServiceMain, mServiceWork);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND && mServiceMain != null) {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_menu, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.main_menu_search).getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Intent intent = new Intent(BROADCAST_SEARCH_FILTER_CHANGED);
                intent.putExtra("text", newText.toLowerCase().trim());
                LocalBroadcastManager.getInstance(MainActivity.this)
                        .sendBroadcast(intent);
                return true;
            }
        });
        return true;
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(BROADCAST_CONTEXT_MENU_CLOSED));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.main_menu_freeze_all) {
            Intent intent = new Intent(DummyActivity.PUBLIC_FREEZE_ALL);
            intent.setComponent(new ComponentName(this, DummyActivity.class));
            startActivity(intent);
            return true;
        } else if (itemId == R.id.main_menu_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            Bundle extras = new Bundle();
            extras.putBinder("profile_service", mServiceWork.asBinder());
            settingsIntent.putExtra("extras", extras);
            startActivity(settingsIntent);
            return true;
        } else if (itemId == R.id.main_menu_create_freeze_all_shortcut) {
            Intent launchIntent = new Intent(DummyActivity.PUBLIC_FREEZE_ALL);
            launchIntent.setComponent(new ComponentName(this, DummyActivity.class));
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Utility.createLauncherShortcut(this, launchIntent,
                    Icon.createWithResource(this, R.mipmap.ic_freeze),
                    "shelter-freeze-all", getString(R.string.freeze_all_shortcut));
            return true;
        } else if (itemId == R.id.main_menu_install_app_to_profile) {
            mSelectApk.launch(null);
            return true;
        } else if (itemId == R.id.main_menu_show_all) {
            Runnable update = () -> {
                mShowAll = !item.isChecked();
                item.setChecked(mShowAll);
                LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(new Intent(AppListFragment.BROADCAST_REFRESH));
            };
            if (!item.isChecked()) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.show_all_warning)
                        .setPositiveButton(R.string.first_run_alert_continue,
                                (dialog, which) -> update.run())
                        .setNegativeButton(R.string.first_run_alert_cancel, null)
                        .show();
            } else {
                update.run();
            }
            return true;
        } else if (itemId == R.id.main_menu_documents_ui) {
            Intent documentsUiIntent = new Intent(Intent.ACTION_VIEW);
            documentsUiIntent.setDataAndType(null, "vnd.android.document/root");
            startActivity(documentsUiIntent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void onApkSelected(Uri uri) {
        if (uri == null) return;
        UriForwardProxy proxy = new UriForwardProxy(getApplicationContext(), uri);
        try {
            mServiceWork.installApk(proxy, new IAppInstallCallback.Stub() {
                @Override
                public void callback(int result) {
                    runOnUiThread(() -> {
                        if (result == RESULT_OK)
                            Toast.makeText(MainActivity.this,
                                    R.string.install_app_to_profile_success, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (RemoteException e) {
            // Well, I don't know what to do then
        }
    }
}
