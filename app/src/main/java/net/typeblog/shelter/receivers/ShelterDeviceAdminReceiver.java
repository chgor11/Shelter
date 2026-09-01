package net.typeblog.shelter.receivers;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ui.DummyActivity;
import net.typeblog.shelter.util.Utility;

public class ShelterDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final int NOTIFICATION_ID = 114514;

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return;

        Intent i = new Intent(
                context.getApplicationContext(),
                DummyActivity.class
        );
        i.setAction(DummyActivity.FINALIZE_PROVISION);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Notification notification = Utility.buildNotification(
                context,
                true,
                "shelter-finish-provision",
                context.getString(R.string.finish_provision_title),
                context.getString(R.string.finish_provision_desc),
                R.drawable.ic_notification_white_24dp
        );

        notification.contentIntent = PendingIntent.getActivity(
                context,
                0,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        context.getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID, notification);
    }

    @Override
    public CharSequence onDisableRequested(
            Context context,
            Intent intent) {

        DevicePolicyManager dpm =
                context.getSystemService(DevicePolicyManager.class);

        ComponentName adminComponent =
                new ComponentName(
                        context,
                        ShelterDeviceAdminReceiver.class
                );

        if (dpm != null) {
            try {
                // Disable Keyguard customization features
                // while the administrator is still active.
                dpm.setKeyguardDisabledFeatures(
                        adminComponent,
                        DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL
                );

                // Immediately lock the device.
                dpm.lockNow();

            } catch (SecurityException ignored) {
                // The operation is not permitted for this
                // administrator/profile on this device.
            }
        }

        return context.getString(
                R.string.device_admin_disable_warning
        );
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
    }
}
