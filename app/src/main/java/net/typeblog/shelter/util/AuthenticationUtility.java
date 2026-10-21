package net.typeblog.shelter.util;

import android.content.Intent;
import android.app.admin.DevicePolicyManager;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
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
            if ("net.typeblog.shelter.action.SET_PARENT_KEYGUARD_POLICY".equals(intent.getAction())) return false;
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
            return timestamp - intentTimestamp < 10 * 1000 &&
                    sign(key, intentTimestamp).equals(intent.getStringExtra("signature"));
        }
    }

    public static void signParentKeyguardPolicyIntent(Intent intent) {
        String key = LocalStorageManager.getInstance().getString(LocalStorageManager.PREF_AUTH_KEY);
        if (key == null || intent == null || intent.getAction() == null) throw new IllegalStateException("Missing auth key or intent");
        intent.putExtra("policy_payload_signature", signPayload(key, policyPayload(intent)));
    }

    public static boolean checkParentKeyguardPolicyIntent(Intent intent) {
        if (intent == null || !"net.typeblog.shelter.action.SET_PARENT_KEYGUARD_POLICY".equals(intent.getAction()) || intent.getExtras() == null) return false;
        String key = LocalStorageManager.getInstance().getString(LocalStorageManager.PREF_AUTH_KEY);
        if (key == null) return false;
        try {
            if (!(intent.getExtras().get("key_name") instanceof String) || !(intent.getExtras().get("value") instanceof Boolean)
                    || !(intent.getExtras().get("policy_key") instanceof Integer) || !(intent.getExtras().get("enabled") instanceof Boolean)
                    || !(intent.getExtras().get("target_user") instanceof String) || !(intent.getExtras().get("timestamp") instanceof Long)
                    || !(intent.getExtras().get("policy_payload_signature") instanceof String)) return false;
            long ts = intent.getLongExtra("timestamp", 0L), now = System.currentTimeMillis();
            if (ts <= 0 || ts < now - 10000L || ts > now + 10000L || !"parent".equals(intent.getStringExtra("target_user"))) return false;
            if (!isValidParentKeyguardPolicyRequest(intent)) return false;
            String expected = signPayload(key, policyPayload(intent)), supplied = intent.getStringExtra("policy_payload_signature");
            return supplied != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException e) { return false; }
    }

    public static boolean isValidParentKeyguardPolicyRequest(Intent intent) {
        if (intent == null || !"net.typeblog.shelter.action.SET_PARENT_KEYGUARD_POLICY".equals(intent.getAction()) || intent.getExtras() == null) return false;
        String name = intent.getStringExtra("key_name"); int policy = intent.getIntExtra("policy_key", Integer.MIN_VALUE);
        boolean value = intent.getBooleanExtra("value", false), enabled = intent.getBooleanExtra("enabled", false);
        return value == enabled && (("settings_disable_parent_trust_agents".equals(name) && policy == DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS)
            || ("settings_disable_parent_fingerprint".equals(name) && policy == DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT)
            || ("settings_disable_parent_face".equals(name) && policy == DevicePolicyManager.KEYGUARD_DISABLE_FACE)
            || ("settings_disable_parent_iris".equals(name) && policy == DevicePolicyManager.KEYGUARD_DISABLE_IRIS));
    }

    private static byte[] policyPayload(Intent intent) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(1); out.writeUTF(intent.getAction() == null ? "" : intent.getAction());
            out.writeUTF(intent.getStringExtra("key_name") == null ? "" : intent.getStringExtra("key_name"));
            out.writeBoolean(intent.getBooleanExtra("value", false)); out.writeInt(intent.getIntExtra("policy_key", Integer.MIN_VALUE));
            out.writeBoolean(intent.getBooleanExtra("enabled", false)); out.writeUTF(intent.getStringExtra("target_user") == null ? "" : intent.getStringExtra("target_user"));
            out.writeLong(intent.getLongExtra("timestamp", 0L)); out.flush(); return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    private static String signPayload(String hexKey, byte[] payload) {
        try { SecretKeySpec spec = new SecretKeySpec(hexStringToByteArray(hexKey), "HmacSHA256"); Mac mac = Mac.getInstance("HmacSHA256"); mac.init(spec); return bytesToHex(mac.doFinal(payload)); }
        catch (NoSuchAlgorithmException | InvalidKeyException e) { throw new RuntimeException("WTF?"); }
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
