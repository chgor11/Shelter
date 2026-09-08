package net.typeblog.shelter.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.typeblog.shelter.ui.DummyActivity;
import net.typeblog.shelter.util.Utility;

/**
 * SECURITY-CRITICAL:
 *
 * Notification-only entry point for "freeze all now".
 *
 * A launcher/notification PendingIntent cannot safely contain a timestamped
 * HMAC at creation time because AuthenticationUtility signatures expire.
 * This receiver therefore creates the signed request only when the user
 * actually activates the notification action.
 *
 * It does not perform the freeze itself. The signed PUBLIC_FREEZE_ALL request
 * is forwarded to the parent profile, which then creates a signed
 * FREEZE_ALL_IN_LIST request for the Work Profile. The actual freeze is
 * finally authorized by DummyActivity's Work Profile five-minute lease gate.
 */
public final class FreezeAllNowReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent receivedIntent) {
        Intent intent = new Intent(DummyActivity.PUBLIC_FREEZE_ALL);
        Utility.transferIntentToProfile(context, intent);
    }
}

