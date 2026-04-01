package bas.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-128-CBC with random IV per encryption (NFR-4 compliance).
 * IV is prepended to ciphertext and extracted on decrypt.
 */
public final class AESUtil {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LEN = 16;
    private AESUtil() {}

    private static SecretKeySpec deriveKey(String passphrase) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(Arrays.copyOf(hash, 16), "AES");
        } catch (Exception e) { throw new RuntimeException("AES key derivation failed", e); }
    }

    public static String encrypt(String plaintext, String passphrase) {
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[IV_LEN + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LEN);
            System.arraycopy(encrypted, 0, combined, IV_LEN, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) { throw new RuntimeException("AES encryption failed", e); }
    }

    public static String decrypt(String ciphertext, String passphrase) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LEN);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LEN, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase), new IvParameterSpec(iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new RuntimeException("AES decryption failed", e); }
    }
}
