package bas.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-128-CBC encryption utility.
 * Used to encrypt/decrypt sensitive configuration data (e.g. SMTP credentials).
 * NFR-4: SSL/TLS encryption compliance support.
 */
public final class AESUtil {

    private static final String ALGORITHM  = "AES/CBC/PKCS5Padding";
    private static final byte[] FIXED_IV   = "BAS_IV_2026_FIX!".getBytes(StandardCharsets.UTF_8); // 16 bytes

    private AESUtil() {}

    /**
     * Derives a 128-bit AES key from an arbitrary passphrase using SHA-256.
     */
    private static SecretKeySpec deriveKey(String passphrase) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(passphrase.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(Arrays.copyOf(hash, 16), "AES");
        } catch (Exception e) {
            throw new RuntimeException("AES key derivation failed", e);
        }
    }

    /**
     * Encrypts plaintext → Base64-encoded ciphertext.
     */
    public static String encrypt(String plaintext, String passphrase) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase), new IvParameterSpec(FIXED_IV));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    /**
     * Decrypts Base64-encoded ciphertext → plaintext.
     */
    public static String decrypt(String ciphertext, String passphrase) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase), new IvParameterSpec(FIXED_IV));
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed", e);
        }
    }
}
