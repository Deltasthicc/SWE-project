package bas.test;

import bas.auth.JWTUtil;
import bas.auth.SessionManager;
import bas.crypto.AESUtil;
import bas.db.DatabaseManager;
import bas.model.User;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Cryptography & Authentication Tests (NFR-3, NFR-4)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestCryptoAndAuth {

    // ═══ AES ENCRYPTION ══════════════════════════════════════════════════════

    @Test @Order(1) @DisplayName("AES: encrypt then decrypt returns original")
    void aesRoundTrip() {
        String original = "Hello BAS System!";
        String key = "testkey123";
        String encrypted = AESUtil.encrypt(original, key);
        String decrypted = AESUtil.decrypt(encrypted, key);
        assertEquals(original, decrypted);
    }

    @Test @Order(2) @DisplayName("AES: encrypted text differs from plaintext")
    void aesEncryptedDiffers() {
        String original = "sensitive data";
        String encrypted = AESUtil.encrypt(original, "key1");
        assertNotEquals(original, encrypted);
    }

    @Test @Order(3) @DisplayName("AES: different keys produce different ciphertext")
    void aesDifferentKeys() {
        String text = "same text";
        String enc1 = AESUtil.encrypt(text, "key1");
        String enc2 = AESUtil.encrypt(text, "key2");
        assertNotEquals(enc1, enc2);
    }

    @Test @Order(4) @DisplayName("AES: wrong key fails to decrypt correctly")
    void aesWrongKey() {
        String encrypted = AESUtil.encrypt("secret", "correctKey");
        assertThrows(RuntimeException.class, () -> AESUtil.decrypt(encrypted, "wrongKey"));
    }

    @Test @Order(5) @DisplayName("AES: empty string encrypts and decrypts")
    void aesEmptyString() {
        String enc = AESUtil.encrypt("", "key");
        assertEquals("", AESUtil.decrypt(enc, "key"));
    }

    @Test @Order(6) @DisplayName("AES: special characters survive round-trip")
    void aesSpecialChars() {
        String original = "P@$$w0rd!#%^&*()_+{}|:<>?~`";
        String enc = AESUtil.encrypt(original, "key");
        assertEquals(original, AESUtil.decrypt(enc, "key"));
    }

    @Test @Order(7) @DisplayName("AES: long text (1000 chars) round-trip")
    void aesLongText() {
        String original = "A".repeat(1000);
        String enc = AESUtil.encrypt(original, "key");
        assertEquals(original, AESUtil.decrypt(enc, "key"));
    }

    // ═══ PASSWORD HASHING ════════════════════════════════════════════════════

    @Test @Order(10) @DisplayName("SHA-256: hash is 64 hex characters")
    void hashLength() { assertEquals(64, DatabaseManager.hash("password").length()); }

    @Test @Order(11) @DisplayName("SHA-256: same input = same hash (deterministic)")
    void hashDeterministic() {
        assertEquals(DatabaseManager.hash("test123"), DatabaseManager.hash("test123"));
    }

    @Test @Order(12) @DisplayName("SHA-256: different input = different hash")
    void hashDifferent() {
        assertNotEquals(DatabaseManager.hash("password1"), DatabaseManager.hash("password2"));
    }

    @Test @Order(13) @DisplayName("SHA-256: hash differs from plaintext")
    void hashNotPlaintext() { assertNotEquals("owner123", DatabaseManager.hash("owner123")); }

    @Test @Order(14) @DisplayName("SHA-256: empty string hashes without error")
    void hashEmpty() { assertNotNull(DatabaseManager.hash("")); assertEquals(64, DatabaseManager.hash("").length()); }

    @Test @Order(15) @DisplayName("SHA-256: Unicode characters hash correctly")
    void hashUnicode() { assertNotNull(DatabaseManager.hash("पासवर्ड")); }

    // ═══ JWT TOKEN ═══════════════════════════════════════════════════════════

    @Test @Order(20) @DisplayName("JWT: generate returns non-null token")
    void jwtGenerate() {
        String token = JWTUtil.generateToken("user1", "Test User", "OWNER");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test @Order(21) @DisplayName("JWT: token has 3 dot-separated parts")
    void jwtStructure() {
        String token = JWTUtil.generateToken("user1", "Test", "CLERK");
        assertEquals(3, token.split("\\.").length);
    }

    @Test @Order(22) @DisplayName("JWT: valid token passes validation")
    void jwtValidate() {
        String token = JWTUtil.generateToken("user1", "Test", "MANAGER");
        String payload = JWTUtil.validateToken(token);
        assertNotNull(payload);
    }

    @Test @Order(23) @DisplayName("JWT: payload contains correct subject")
    void jwtSubject() {
        String token = JWTUtil.generateToken("clerk1", "Arjun", "CLERK");
        String payload = JWTUtil.validateToken(token);
        assertEquals("clerk1", JWTUtil.extractClaim(payload, "sub"));
    }

    @Test @Order(24) @DisplayName("JWT: payload contains correct role")
    void jwtRole() {
        String token = JWTUtil.generateToken("owner1", "Ravi", "OWNER");
        String payload = JWTUtil.validateToken(token);
        assertEquals("OWNER", JWTUtil.extractClaim(payload, "role"));
    }

    @Test @Order(25) @DisplayName("JWT: payload contains correct name")
    void jwtName() {
        String token = JWTUtil.generateToken("mgr1", "Priya Mehta", "MANAGER");
        String payload = JWTUtil.validateToken(token);
        assertEquals("Priya Mehta", JWTUtil.extractClaim(payload, "name"));
    }

    @Test @Order(26) @DisplayName("JWT: tampered token fails validation")
    void jwtTampered() {
        String token = JWTUtil.generateToken("user1", "Test", "OWNER");
        // Flip a character in the signature
        String tampered = token.substring(0, token.length()-2) + "XX";
        assertNull(JWTUtil.validateToken(tampered));
    }

    @Test @Order(27) @DisplayName("JWT: completely garbage string fails")
    void jwtGarbage() { assertNull(JWTUtil.validateToken("not.a.jwt.at.all")); }

    @Test @Order(28) @DisplayName("JWT: null token fails")
    void jwtNull() { assertNull(JWTUtil.validateToken(null)); }

    @Test @Order(29) @DisplayName("JWT: empty token fails")
    void jwtEmpty() { assertNull(JWTUtil.validateToken("")); }

    @Test @Order(30) @DisplayName("JWT: token with modified payload fails signature check")
    void jwtPayloadTampered() {
        String token = JWTUtil.generateToken("user1", "Test", "CLERK");
        String[] parts = token.split("\\.");
        // Replace payload with base64 of different content
        String fakePayload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"sub\":\"hacker\",\"role\":\"OWNER\",\"exp\":9999999999}".getBytes());
        String tampered = parts[0] + "." + fakePayload + "." + parts[2];
        assertNull(JWTUtil.validateToken(tampered));
    }

    // ═══ SESSION MANAGER ═════════════════════════════════════════════════════

    @Test @Order(40) @DisplayName("Session: not authenticated before login")
    void sessionNotAuth() {
        SessionManager.getInstance().logout();
        assertFalse(SessionManager.getInstance().isAuthenticated());
    }

    @Test @Order(41) @DisplayName("Session: authenticated after login")
    void sessionAuth() {
        User u = new User("test1", "Test", DatabaseManager.hash("pw"), User.Role.OWNER);
        SessionManager.getInstance().login(u);
        assertTrue(SessionManager.getInstance().isAuthenticated());
    }

    @Test @Order(42) @DisplayName("Session: correct role after login")
    void sessionRole() {
        User u = new User("test1", "Test", DatabaseManager.hash("pw"), User.Role.CLERK);
        SessionManager.getInstance().login(u);
        assertTrue(SessionManager.getInstance().hasRole(User.Role.CLERK));
        assertFalse(SessionManager.getInstance().hasRole(User.Role.OWNER));
    }

    @Test @Order(43) @DisplayName("Session: hasRole accepts multiple allowed roles")
    void sessionMultiRole() {
        User u = new User("test1", "Test", DatabaseManager.hash("pw"), User.Role.MANAGER);
        SessionManager.getInstance().login(u);
        assertTrue(SessionManager.getInstance().hasRole(User.Role.MANAGER, User.Role.OWNER));
    }

    @Test @Order(44) @DisplayName("Session: not authenticated after logout")
    void sessionLogout() {
        User u = new User("test1", "Test", DatabaseManager.hash("pw"), User.Role.OWNER);
        SessionManager.getInstance().login(u);
        assertTrue(SessionManager.getInstance().isAuthenticated());
        SessionManager.getInstance().logout();
        assertFalse(SessionManager.getInstance().isAuthenticated());
    }

    @Test @Order(45) @DisplayName("Session: requireRole throws for wrong role")
    void sessionRequireRoleThrows() {
        User u = new User("test1", "Test", DatabaseManager.hash("pw"), User.Role.CLERK);
        SessionManager.getInstance().login(u);
        assertThrows(SecurityException.class, () ->
            SessionManager.getInstance().requireRole(User.Role.OWNER));
    }

    @Test @Order(46) @DisplayName("Session: requireRole passes for correct role")
    void sessionRequireRolePasses() {
        User u = new User("test1", "Test", DatabaseManager.hash("pw"), User.Role.OWNER);
        SessionManager.getInstance().login(u);
        assertDoesNotThrow(() ->
            SessionManager.getInstance().requireRole(User.Role.OWNER, User.Role.MANAGER));
    }

    @Test @Order(47) @DisplayName("Session: getToken returns non-null after login")
    void sessionToken() {
        User u = new User("test1", "Test User", DatabaseManager.hash("pw"), User.Role.OWNER);
        SessionManager.getInstance().login(u);
        assertNotNull(SessionManager.getInstance().getToken());
        // Token should be a valid JWT
        assertNotNull(JWTUtil.validateToken(SessionManager.getInstance().getToken()));
    }
}
