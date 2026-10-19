package net.typeblog.shelter.util;

import android.content.Intent;
import android.app.admin.DevicePolicyManager;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// Opening access to actions across the profile boundary poses a security risk
// The risk is that other applications might also be able to start our activities
// through system's IntentForwarderActivity
// That activity runs in the system process, thus normal limitations like "permissions"
// and "exported" will not work.
// This class tries to fix it by appending a timestamp and a signature of the timestamp
// to our own Intents sent through the boundary, ensuring that only Shelter can invoke
// its high-privilege functions across that boundary, assuming that no other application
// would be able to access Shelter's internal storage to gain access to the private key.
// The private key is generated the first time this class is used, and then shared
// across the profile boundary. Shelter will always trust the first key it receives.
public class AuthenticationUtility {
    public static void signIntent(Intent intent) {
        String key = LocalStorageManager.getInstance().getString(
                LocalStorageManager.PREF_AUTH_KEY);
        if (key == null) {
            // Generate the key if we don't have one yet
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
                keyGen.init(256);
                key = bytesToHex(keyGen.generateKey().getEncoded());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("WTF?");
            }

            LocalStorageManager.getInstance().setString(
                    LocalStorageManager.PREF_AUTH_KEY, key);

            // If this is the first time, we just send the key to the other side
            intent.putExtra("auth_key", key);
        } else {
            long timestamp = new Date().getTime();
            intent.putExtra("timestamp", timestamp);
            intent.putExtra("signature", sign(key, timestamp));
        }
    }

    public static boolean checkIntent(Intent intent) {
        String key = LocalStorageManager.getInstance().getString(
                LocalStorageManager.PREF_AUTH_KEY);
        if (key == null) {
            // A sensitive policy request must never bootstrap the shared key,
            // because an attacker could choose the key and matching signature.
            if ("net.typeblog.shelter.action.SET_PARENT_KEYGUARD_POLICY".equals(intent.getAction())) {
                return false;
            }
            // If we haven't got a key yet, we just take the key sent by the other side
            // If not, NEVER receive any key because it can be fake.
            // We only trust the first key we receive
            if (intent.hasExtra("auth_key")) {
                LocalStorageManager.getInstance().setString(
                        LocalStorageManager.PREF_AUTH_KEY, intent.getStringExtra("auth_key"));
                return true;
            } else {
                // We haven't got a key, and we can't check if it is true or not.
                return false;
            }
        } else {
            long timestamp = new Date().getTime();
            long intentTimestamp = intent.getLongExtra("timestamp", 0);
            return timestamp - intentTimestamp < 30 * 1000 &&
                    sign(key, intentTimestamp).equals(intent.getStringExtra("signature"));
        }
    }


    /**
     * Adds a second, content-bound HMAC for the four parent keyguard policy
     * requests. Call this only after signIntent() has populated the normal
     * timestamp/signature (and, during initial setup, auth_key) fields.
     *
     * The content HMAC covers the action, UI key name, requested value,
     * Android policy bit, enabled state, target user ID and timestamp.
     */
    public static void signParentKeyguardPolicyIntent(Intent intent) {
        String key = LocalStorageManager.getInstance().getString(
                LocalStorageManager.PREF_AUTH_KEY);
        if (key == null || intent == null || intent.getAction() == null) {
            throw new IllegalStateException("Cannot sign parent keyguard policy without an auth key");
        }
        long timestamp = intent.getLongExtra("timestamp", 0L);
        intent.putExtra("policy_payload_signature",
                sign(key, parentKeyguardPolicyPayload(intent, timestamp)));
    }

    /**
     * Verifies the content-bound HMAC and the target user before any policy
     * operation is attempted. The ordinary timestamp signature is checked
     * separately by checkIntent().
     */
    public static boolean checkParentKeyguardPolicyIntent(Intent intent) {
        if (intent == null
                || !"net.typeblog.shelter.action.SET_PARENT_KEYGUARD_POLICY".equals(intent.getAction())
                || intent.getExtras() == null) {
            return false;
        }

        String key = LocalStorageManager.getInstance().getString(
                LocalStorageManager.PREF_AUTH_KEY);
        if (key == null) {
            return false;
        }

        try {
            if (!(intent.getExtras().get("key_name") instanceof String)
                    || !(intent.getExtras().get("value") instanceof Boolean)
                    || !(intent.getExtras().get("policy_key") instanceof Integer)
                    || !(intent.getExtras().get("enabled") instanceof Boolean)
                    || !(intent.getExtras().get("target_user") instanceof Integer)
                    || !(intent.getExtras().get("timestamp") instanceof Long)
                    || !(intent.getExtras().get("policy_payload_signature") instanceof String)) {
                return false;
            }

            long timestamp = intent.getLongExtra("timestamp", 0L);
            long now = System.currentTimeMillis();
            // Reject both stale and future-dated requests.
            if (timestamp <= 0L || timestamp < now - 30_000L || timestamp > now + 30_000L) {
                return false;
            }

            String keyName = intent.getStringExtra("key_name");
            int policyKey = intent.getIntExtra("policy_key", Integer.MIN_VALUE);
            boolean value = intent.getBooleanExtra("value", false);
            boolean enabled = intent.getBooleanExtra("enabled", false);
            if (value != enabled || !isValidParentKeyguardPolicy(keyName, policyKey)) {
                return false;
            }

            String expected = sign(key, parentKeyguardPolicyPayload(intent, timestamp));
            String supplied = intent.getStringExtra("policy_payload_signature");
            if (supplied == null || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    supplied.getBytes(StandardCharsets.US_ASCII))) {
                return false;
            }

            // The target user is verified against the real profile parent by
            // the context-aware overload below.
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Context-aware variant which additionally confirms that target_user is
     * this managed profile's actual parent user.
     */
    public static boolean checkParentKeyguardPolicyIntent(Intent intent, android.content.Context context) {
        if (!checkParentKeyguardPolicyIntent(intent) || context == null) {
            return false;
        }
        try {
            UserManager userManager = context.getSystemService(UserManager.class);
            if (userManager == null) {
                return false;
            }
            UserHandle parent = userManager.getProfileParent(Process.myUserHandle());
            return parent != null
                    && intent.getIntExtra("target_user", Integer.MIN_VALUE)
                    == parent.getIdentifier();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean isValidParentKeyguardPolicyRequest(Intent intent) {
        if (intent == null
                || !"net.typeblog.shelter.action.SET_PARENT_KEYGUARD_POLICY".equals(intent.getAction())
                || intent.getExtras() == null) {
            return false;
        }
        String keyName = intent.getStringExtra("key_name");
        int policyKey = intent.getIntExtra("policy_key", Integer.MIN_VALUE);
        boolean value = intent.getBooleanExtra("value", false);
        boolean enabled = intent.getBooleanExtra("enabled", false);
        return value == enabled && isValidParentKeyguardPolicy(keyName, policyKey);
    }

    private static boolean isValidParentKeyguardPolicy(String keyName, int policyKey) {
        return ("settings_disable_parent_trust_agents".equals(keyName)
                    && policyKey == DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS)
                || ("settings_disable_parent_fingerprint".equals(keyName)
                    && policyKey == DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT)
                || ("settings_disable_parent_face".equals(keyName)
                    && policyKey == DevicePolicyManager.KEYGUARD_DISABLE_FACE)
                || ("settings_disable_parent_iris".equals(keyName)
                    && policyKey == DevicePolicyManager.KEYGUARD_DISABLE_IRIS);
    }

    private static byte[] parentKeyguardPolicyPayload(Intent intent, long timestamp) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(1); // protocol version
            out.writeUTF(intent.getAction() == null ? "" : intent.getAction());
            out.writeUTF(intent.getStringExtra("key_name") == null
                    ? "" : intent.getStringExtra("key_name"));
            out.writeBoolean(intent.getBooleanExtra("value", false));
            out.writeInt(intent.getIntExtra("policy_key", Integer.MIN_VALUE));
            out.writeBoolean(intent.getBooleanExtra("enabled", false));
            out.writeInt(intent.getIntExtra("target_user", Integer.MIN_VALUE));
            out.writeLong(timestamp);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to encode parent keyguard policy payload", e);
        }
    }

    private static String sign(String hexKey, byte[] payload) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(hexStringToByteArray(hexKey), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            return bytesToHex(mac.doFinal(payload));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("WTF?");
        }
    }

    public static void reset() {
        LocalStorageManager.getInstance().remove(LocalStorageManager.PREF_AUTH_KEY);
    }

    private static String sign(String hexKey, long timestamp) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(hexStringToByteArray(hexKey), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            return bytesToHex(mac.doFinal(longToBytes(timestamp)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("WTF?");
        }
    }

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


    private static byte[] hexStringToByteArray(String s) {
        try {
            int len = s.length();
            if (len > 1) {
                byte[] data = new byte[len / 2];
                for (int i = 0 ; i < len ; i += 2) {
                    data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                            + Character.digit(s.charAt(i + 1), 16));
                }
                return data;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }
}
