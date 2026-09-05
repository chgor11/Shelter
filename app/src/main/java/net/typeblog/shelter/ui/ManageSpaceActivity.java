package net.typeblog.shelter.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import net.typeblog.shelter.util.Utility;

public class ManageSpaceActivity extends SecureActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Shelter in the Work Profile is the Profile Owner.
         *
         * Do absolutely nothing in that profile.
         */
        if (Utility.isProfileOwner(this)) {
            finish();
            return;
        }

        /*
         * We are running in the Personal/Main Profile.
         *
         * Obtain the actual launcher intent registered for this
         * package instead of constructing a new Intent manually.
         * This makes "Manage storage" follow the same entry point
         * as tapping the Shelter icon in the launcher.
         */
        PackageManager packageManager = getPackageManager();

        Intent launchIntent =
                packageManager.getLaunchIntentForPackage(getPackageName());

        if (launchIntent != null) {
            startActivity(launchIntent);
        }

        finish();
    }
}
