package net.typeblog.shelter.util;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Authentication and request-integrity boundary for cross-profile Shelter Intents.
 *
 * SECURITY MODEL
 * --------------
 * The Work Profile copy of Shelter receives Intents through Android's
 * cross-profile forwarding machinery.  An exported Activity therefore cannot be
 * treated as a private IPC endpoint.  This class provides application-level
 * authentication and, importantly, signs the action and every security-sensitive
 * extra which DummyActivity accepts.
 *
 * Protocol v2:
 *   version + action + timestamp + nonce + canonical action-specific extras
 *   are authenticated with HMAC-SHA256.
 *
 * The first request after provisioning is still a trust-on-first-use bootstrap:
 * the receiving profile stores the 256-bit key supplied by the other Shelter
 * profile.  This is retained for compatibility with the existing provisioning
 * flow.  Once a key exists, requests require the full v2 signature.
 */
public final class AuthenticationUtility {
    private static final String EXTRA_AUTH_KEY = "auth_key";
    private static final String EXTRA_TIMESTAMP = "timestamp";
    private static final String EXTRA_NONCE = "nonce";
    private static final String EXTRA_SIGNATURE = "signature";

    private static final int PROTOCOL_VERSION = 2;
    private static final long MAX_CLOCK_SKEW_MILLIS = 30_000L;
    private static final int NONCE_BYTES = 32;
    private static final int MAX_STORED_NONCES = 32;
    private static final String PREF_USED_NONCES = "auth_used_nonces_v2";

    private AuthenticationUtility() {
    }

    public static void signIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            throw new IllegalArgumentException("Cannot sign an Intent without an action");
        }

        String key = LocalStorageManager.getInstance().getString(
                LocalStorageManager.PREF_AUTH_KEY);

        if (key == null) {
            key = generateKey();
            LocalStorageManager.getInstance().setString(
                    LocalStorageManager.PREF_AUTH_KEY, key);

            // Compatibility bootstrap.  The first receiver stores this key and
            // all subsequent requests use the signed v2 protocol.
            intent.putExtra(EXTRA_AUTH_KEY, key);
            intent.removeExtra(EXTRA_TIMESTAMP);
            intent.removeExtra(EXTRA_NONCE);
            intent.removeExtra(EXTRA_SIGNATURE);
            return;
        }

        long timestamp = System.currentTimeMillis();
        String nonce = generateNonce();

        intent.putExtra(EXTRA_TIMESTAMP, timestamp);
        intent.putExtra(EXTRA_NONCE, nonce);
        intent.putExtra(
                EXTRA_SIGNATURE,
                sign(key, buildCanonicalPayload(intent, timestamp, nonce)));
    }

    public static boolean checkIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return false;
        }

        try {
            String key = LocalStorageManager.getInstance().getString(
                    LocalStorageManager.PREF_AUTH_KEY);

            if (key == null) {
                String bootstrapKey = intent.getStringExtra(EXTRA_AUTH_KEY);
                // Bootstrap is deliberately restricted to the harmless
                // Work Profile availability probe.  A first-time caller must
                // not be able to establish a new key while simultaneously
                // requesting an administrative operation.
                if (!net.typeblog.shelter.ui.DummyActivity.TRY_START_SERVICE
                        .equals(intent.getAction())
                        || !isValidHexKey(bootstrapKey)) {
                    return false;
                }

                // Do not accept a bootstrap key together with a normal v2
                // signature.  Bootstrap is a separate protocol state.
                if (intent.hasExtra(EXTRA_TIMESTAMP)
                        || intent.hasExtra(EXTRA_NONCE)
                        || intent.hasExtra(EXTRA_SIGNATURE)) {
                    return false;
                }

                LocalStorageManager.getInstance().setString(
                        LocalStorageManager.PREF_AUTH_KEY, bootstrapKey);
                return true;
            }

            if (!isValidHexKey(key)) {
                return false;
            }

            if (intent.hasExtra(EXTRA_AUTH_KEY)) {
                // Once a key exists, a new key must never be accepted.
                return false;
            }

            if (!intent.hasExtra(EXTRA_TIMESTAMP)
                    || !intent.hasExtra(EXTRA_NONCE)
                    || !intent.hasExtra(EXTRA_SIGNATURE)) {
                return false;
            }

            long timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, Long.MIN_VALUE);
            String nonce = intent.getStringExtra(EXTRA_NONCE);
            String suppliedSignature = intent.getStringExtra(EXTRA_SIGNATURE);

            if (!isFreshTimestamp(timestamp)
                    || !isValidNonce(nonce)
                    || !isValidHexSignature(suppliedSignature)) {
                return false;
            }

            String expectedSignature = sign(
                    key,
                    buildCanonicalPayload(intent, timestamp, nonce));

            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.US_ASCII),
                    suppliedSignature.getBytes(StandardCharsets.US_ASCII))) {
                return false;
            }

            // Replay protection is deliberately performed only after HMAC
            // verification.  A nonce from an unauthenticated caller therefore
            // cannot poison the replay cache.
            return rememberNonceIfUnused(nonce);
        } catch (RuntimeException e) {
            // Malformed/malicious Parcels must fail closed.
            return false;
        }
    }

    public static void reset() {
        LocalStorageManager.getInstance().remove(LocalStorageManager.PREF_AUTH_KEY);
        LocalStorageManager.getInstance().remove(PREF_USED_NONCES);
    }

    private static String generateKey() {
        byte[] bytes = new byte[NONCE_BYTES];
        new SecureRandom().nextBytes(bytes);
        return bytesToHex(bytes);
    }

    private static String generateNonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        new SecureRandom().nextBytes(bytes);
        return bytesToHex(bytes);
    }

    private static boolean isFreshTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        return timestamp >= now - MAX_CLOCK_SKEW_MILLIS
                && timestamp <= now + MAX_CLOCK_SKEW_MILLIS;
    }

    private static boolean rememberNonceIfUnused(String nonce) {
        synchronized (AuthenticationUtility.class) {
            LocalStorageManager storage = LocalStorageManager.getInstance();
            String stored = storage.getString(PREF_USED_NONCES);
            List<String> nonces = new ArrayList<>();

            if (stored != null && !stored.isEmpty()) {
                nonces.addAll(Arrays.asList(stored.split(",")));
            }

            if (nonces.contains(nonce)) {
                return false;
            }

            nonces.add(nonce);
            while (nonces.size() > MAX_STORED_NONCES) {
                nonces.remove(0);
            }

            storage.setString(PREF_USED_NONCES, join(nonces));
            return true;
        }
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(value);
        }
        return result.toString();
    }

    /**
     * Builds the exact bytes authenticated by HMAC.  Only action-specific,
     * security-relevant extras are included.  DummyActivity rejects every
     * other extra for signed management actions, so an attacker cannot add an
     * unsigned control parameter after authentication.
     */
    private static String buildCanonicalPayload(
            Intent intent,
            long timestamp,
            String nonce) {

        StringBuilder out = new StringBuilder(512);
        append(out, "version", Integer.toString(PROTOCOL_VERSION));
        append(out, "action", intent.getAction());
        append(out, "timestamp", Long.toString(timestamp));
        append(out, "nonce", nonce);

        String action = intent.getAction();

        if (net.typeblog.shelter.ui.DummyActivity.INSTALL_PACKAGE.equals(action)) {
            appendOptionalString(out, intent, "package");
            appendOptionalString(out, intent, "apk");
            appendOptionalUri(out, intent, "direct_install_apk");
            appendOptionalStringArray(out, intent, "split_apks");
            appendCallbackPresence(out, intent, "callback");
        } else if (net.typeblog.shelter.ui.DummyActivity.UNINSTALL_PACKAGE.equals(action)) {
            appendOptionalString(out, intent, "package");
            appendCallbackPresence(out, intent, "callback");
        } else if (net.typeblog.shelter.ui.DummyActivity.UNFREEZE_AND_LAUNCH.equals(action)) {
            appendOptionalString(out, intent, "packageName");
            appendOptionalBoolean(out, intent, "shouldFreeze");
            appendOptionalStringArray(out, intent, "linkedPackages");
            appendOptionalBooleanArray(out, intent, "linkedPackagesShouldFreeze");
        } else if (net.typeblog.shelter.ui.DummyActivity.FREEZE_ALL_IN_LIST.equals(action)) {
            appendOptionalStringArray(out, intent, "list");
        } else if (net.typeblog.shelter.ui.DummyActivity.SYNCHRONIZE_PREFERENCE.equals(action)) {
            appendOptionalString(out, intent, "name");
            appendOptionalBoolean(out, intent, "boolean");
            appendOptionalInt(out, intent, "int");
        } else if (net.typeblog.shelter.ui.DummyActivity.START_FILE_SHUTTLE.equals(action)
                || net.typeblog.shelter.ui.DummyActivity.START_FILE_SHUTTLE_2.equals(action)) {
            appendBundleBinderPresence(out, intent, "extra", "callback");
        }

        return out.toString();
    }

    private static void appendOptionalString(StringBuilder out, Intent intent, String key) {
        append(out, key + ".present", Boolean.toString(intent.hasExtra(key)));
        if (intent.hasExtra(key)) {
            append(out, key, intent.getStringExtra(key));
        }
    }

    private static void appendOptionalBoolean(StringBuilder out, Intent intent, String key) {
        append(out, key + ".present", Boolean.toString(intent.hasExtra(key)));
        if (intent.hasExtra(key)) {
            append(out, key, Boolean.toString(intent.getBooleanExtra(key, false)));
        }
    }

    private static void appendOptionalInt(StringBuilder out, Intent intent, String key) {
        append(out, key + ".present", Boolean.toString(intent.hasExtra(key)));
        if (intent.hasExtra(key)) {
            append(out, key, Integer.toString(intent.getIntExtra(key, Integer.MIN_VALUE)));
        }
    }

    private static void appendOptionalStringArray(StringBuilder out, Intent intent, String key) {
        append(out, key + ".present", Boolean.toString(intent.hasExtra(key)));
        if (!intent.hasExtra(key)) {
            return;
        }

        String[] values = intent.getStringArrayExtra(key);
        if (values == null) {
            append(out, key + ".null", "true");
            return;
        }

        append(out, key + ".null", "false");
        append(out, key + ".count", Integer.toString(values.length));
        for (int i = 0; i < values.length; i++) {
            append(out, key + "[" + i + "]", values[i]);
        }
    }

    private static void appendOptionalBooleanArray(StringBuilder out, Intent intent, String key) {
        append(out, key + ".present", Boolean.toString(intent.hasExtra(key)));
        if (!intent.hasExtra(key)) {
            return;
        }

        boolean[] values = intent.getBooleanArrayExtra(key);
        if (values == null) {
            append(out, key + ".null", "true");
            return;
        }

        append(out, key + ".null", "false");
        append(out, key + ".count", Integer.toString(values.length));
        for (int i = 0; i < values.length; i++) {
            append(out, key + "[" + i + "]", Boolean.toString(values[i]));
        }
    }

    private static void appendOptionalUri(StringBuilder out, Intent intent, String key) {
        append(out, key + ".present", Boolean.toString(intent.hasExtra(key)));
        if (!intent.hasExtra(key)) {
            return;
        }

        Uri uri = getUriExtra(intent, key);
        append(out, key, uri == null ? null : uri.toString());
    }

    private static void appendCallbackPresence(StringBuilder out, Intent intent, String key) {
        append(out, key + ".present", Boolean.toString(intent.hasExtra(key)));
        if (!intent.hasExtra(key)) {
            return;
        }

        Bundle bundle = intent.getBundleExtra(key);
        append(out, key + ".bundle", Boolean.toString(bundle != null));
        append(out, key + ".binder", Boolean.toString(
                bundle != null && bundle.getBinder("callback") != null));
    }

    private static void appendBundleBinderPresence(
            StringBuilder out,
            Intent intent,
            String bundleKey,
            String binderKey) {
        append(out, bundleKey + ".present", Boolean.toString(intent.hasExtra(bundleKey)));
        if (!intent.hasExtra(bundleKey)) {
            return;
        }

        Bundle bundle = intent.getBundleExtra(bundleKey);
        append(out, bundleKey + ".bundle", Boolean.toString(bundle != null));
        append(out, bundleKey + ".binder", Boolean.toString(
                bundle != null && bundle.getBinder(binderKey) != null));
    }

    private static Uri getUriExtra(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(key, Uri.class);
        }
        //noinspection deprecation
        return intent.getParcelableExtra(key);
    }

    private static void append(StringBuilder out, String key, String value) {
        out.append(escape(key))
                .append('=')
                .append(escape(value == null ? "<null>" : value))
                .append('\n');
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("=", "\\=");
    }

    private static String sign(String hexKey, String payload) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    hexStringToByteArray(hexKey),
                    "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            return bytesToHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate HMAC", e);
        }
    }

    private static boolean isValidHexKey(String value) {
        return value != null
                && value.length() == NONCE_BYTES * 2
                && isHex(value);
    }

    private static boolean isValidHexSignature(String value) {
        return value != null
                && value.length() == 64
                && isHex(value);
    }

    private static boolean isValidNonce(String value) {
        return value != null
                && value.length() == NONCE_BYTES * 2
                && isHex(value);
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= '0' && c <= '9')
                    && !(c >= 'a' && c <= 'f')
                    && !(c >= 'A' && c <= 'F')) {
                return false;
            }
        }
        return true;
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static byte[] hexStringToByteArray(String value) {
        if (value == null || value.length() % 2 != 0 || !isHex(value)) {
            throw new IllegalArgumentException("Invalid hexadecimal key");
        }

        byte[] data = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(value.charAt(i), 16) << 4)
                    + Character.digit(value.charAt(i + 1), 16));
        }
        return data;
    }
}
