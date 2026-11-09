package dev.opencloud.region;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Decoder for the .oc plugin container.
 *
 * Layout: "OCP1" | 12-byte GCM nonce | AES-256-GCM ciphertext + 16-byte tag.
 * The key is not stored as plain bytes: a masked copy is combined with a
 * SHA-256 mask at runtime.  This keeps the format from being trivially
 * readable, but a client-side key can always be recovered from the APK by a
 * determined reverse engineer; see docs/plugins.md.
 */
final class PluginCrypto {
    static final int MAX_PLUGIN_BYTES = 1024 * 1024;

    private static final byte[] MAGIC = {'O', 'C', 'P', '1'};
    private static final int NONCE = 12;
    private static final int TAG = 16;
    private static final String MASK_SEED = "opencloud-plugin-mask-v1";
    private static final String MASKED_KEY = "ynY8jxgOP6ZXZGSiu9Jg8CjGE3OoR554LxUhoSVbHvU=";

    private PluginCrypto() {
    }

    static String decode(InputStream input) throws Exception {
        byte[] data = readAll(input);
        if (data.length < MAGIC.length + NONCE + TAG) {
            throw new IllegalArgumentException("file is too short for a .oc plugin");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                throw new IllegalArgumentException("not an OpenCloud .oc plugin");
            }
        }
        byte[] nonce = new byte[NONCE];
        System.arraycopy(data, MAGIC.length, nonce, 0, NONCE);
        int offset = MAGIC.length + NONCE;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"),
                new GCMParameterSpec(TAG * 8, nonce));
        byte[] plain = cipher.doFinal(data, offset, data.length - offset);
        return new String(plain, "UTF-8");
    }

    private static byte[] key() throws Exception {
        byte[] masked = Base64.decode(MASKED_KEY, Base64.NO_WRAP);
        byte[] mask = MessageDigest.getInstance("SHA-256")
                .digest(MASK_SEED.getBytes("UTF-8"));
        byte[] key = new byte[masked.length];
        for (int i = 0; i < masked.length; i++) {
            key[i] = (byte) (masked[i] ^ mask[i % mask.length]);
        }
        return key;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_PLUGIN_BYTES) {
                throw new IOException("plugin is larger than 1 MB");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
