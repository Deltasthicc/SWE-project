package bas.auth;

import bas.config.AppConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Lightweight JWT (JSON Web Token) utility — no external library needed.
 *
 * Structure: base64url(header) . base64url(payload) . base64url(HMAC-SHA256 signature)
 *
 * Used for role-based session authentication (NFR-3).
 * Tokens carry userId, name, and role; expire after {@link AppConfig#JWT_EXPIRY_HOURS}.
 */
public final class JWTUtil {

    private static final String ALGORITHM = "HmacSHA256";

    private JWTUtil() {}

    // ── Token generation ──────────────────────────────────────────────────────

    /**
     * Creates a signed JWT for the given user.
     *
     * @param userId  unique user identifier (subject claim)
     * @param name    display name
     * @param role    user role: OWNER, MANAGER, CLERK
     * @return signed JWT string
     */
    public static String generateToken(String userId, String name, String role) {
        long now = System.currentTimeMillis() / 1000;
        long exp = now + (AppConfig.JWT_EXPIRY_HOURS * 3600);

        String header  = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = String.format(
            "{\"sub\":\"%s\",\"name\":\"%s\",\"role\":\"%s\",\"iat\":%d,\"exp\":%d}",
            escapeJson(userId), escapeJson(name), escapeJson(role), now, exp);

        String headerB64  = base64url(header.getBytes(StandardCharsets.UTF_8));
        String payloadB64 = base64url(payload.getBytes(StandardCharsets.UTF_8));
        String sigInput   = headerB64 + "." + payloadB64;

        String signature = base64url(hmacSha256(sigInput, AppConfig.JWT_SECRET));

        return sigInput + "." + signature;
    }

    // ── Token validation ──────────────────────────────────────────────────────

    /**
     * Validates a JWT: checks structure, signature, and expiry.
     *
     * @return decoded payload as raw JSON string, or {@code null} if invalid/expired
     */
    public static String validateToken(String token) {
        if (token == null || token.isBlank()) return null;

        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        // Verify signature
        String sigInput    = parts[0] + "." + parts[1];
        String expectedSig = base64url(hmacSha256(sigInput, AppConfig.JWT_SECRET));
        if (!constantTimeEquals(expectedSig, parts[2])) return null;

        // Decode payload
        String payload = new String(
            Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

        // Check expiry
        long exp = extractLong(payload, "exp");
        if (exp > 0 && System.currentTimeMillis() / 1000 > exp) return null;

        return payload;
    }

    // ── Claim extraction ──────────────────────────────────────────────────────

    /** Extracts a string claim from a decoded JWT payload. */
    public static String extractClaim(String payload, String key) {
        // Simple JSON string extraction: "key":"value"
        String search = "\"" + key + "\":\"";
        int start = payload.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = payload.indexOf("\"", start);
        return end > start ? payload.substring(start, end) : null;
    }

    /** Extracts a numeric claim from a decoded JWT payload. */
    public static long extractLong(String payload, String key) {
        String search = "\"" + key + "\":";
        int start = payload.indexOf(search);
        if (start < 0) return -1;
        start += search.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < payload.length(); i++) {
            char c = payload.charAt(i);
            if (Character.isDigit(c)) sb.append(c); else break;
        }
        try { return Long.parseLong(sb.toString()); }
        catch (NumberFormatException e) { return -1; }
    }

    // ── Crypto internals ──────────────────────────────────────────────────────

    private static byte[] hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    private static String base64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** Timing-safe comparison to prevent timing attacks on signature. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
