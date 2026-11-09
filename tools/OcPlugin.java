import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Build and inspect OpenCloud .oc plugin files.
 *
 * Reads the same obfuscated key as the Android PluginCrypto class.  Run it
 * through tools/ocplugin.sh so the class path and output directory are set up
 * for you.
 */
public final class OcPlugin {
    private static final byte[] MAGIC = {'O', 'C', 'P', '1'};
    private static final int NONCE = 12;
    private static final int TAG = 16;
    private static final String MASK_SEED = "opencloud-plugin-mask-v1";
    private static final String MASKED_KEY = "ynY8jxgOP6ZXZGSiu9Jg8CjGE3OoR554LxUhoSVbHvU=";

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !(args[0].equals("pack") || args[0].equals("unpack"))) {
            System.err.println("usage: ocplugin (pack|unpack) <input> <output>");
            System.exit(2);
        }
        byte[] data = Files.readAllBytes(Paths.get(args[1]));
        if (args[0].equals("pack")) {
            byte[] nonce = new byte[NONCE];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"),
                    new GCMParameterSpec(TAG * 8, nonce));
            byte[] ciphertext = cipher.doFinal(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(MAGIC);
            out.write(nonce);
            out.write(ciphertext);
            Files.write(Paths.get(args[2]), out.toByteArray());
            System.out.println("packed " + data.length + " bytes -> " + args[2]);
        } else {
            if (data.length < MAGIC.length + NONCE + TAG) {
                throw new IllegalArgumentException("file is too short");
            }
            for (int i = 0; i < MAGIC.length; i++) {
                if (data[i] != MAGIC[i]) {
                    throw new IllegalArgumentException("not an .oc plugin");
                }
            }
            byte[] nonce = new byte[NONCE];
            System.arraycopy(data, MAGIC.length, nonce, 0, NONCE);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"),
                    new GCMParameterSpec(TAG * 8, nonce));
            byte[] plain = cipher.doFinal(data, MAGIC.length + NONCE,
                    data.length - MAGIC.length - NONCE);
            Files.write(Paths.get(args[2]), plain);
            System.out.println("unpacked " + args[2]);
        }
    }

    private static byte[] key() throws Exception {
        byte[] masked = Base64.getDecoder().decode(MASKED_KEY);
        byte[] mask = MessageDigest.getInstance("SHA-256")
                .digest(MASK_SEED.getBytes(StandardCharsets.UTF_8));
        byte[] key = new byte[masked.length];
        for (int i = 0; i < masked.length; i++) {
            key[i] = (byte) (masked[i] ^ mask[i % mask.length]);
        }
        return key;
    }
}
