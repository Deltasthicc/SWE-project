package bas.config;

public final class AppConfig {
    private AppConfig() {}
    private static String env(String key, String fallback) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : fallback;
    }
    private static int envInt(String key, int fallback) {
        try { return Integer.parseInt(System.getenv(key)); } catch (Exception e) { return fallback; }
    }
    public static final String DB_HOST     = env("BAS_DB_HOST",     "aws-1-ap-south-1.pooler.supabase.com");
    public static final int    DB_PORT     = envInt("BAS_DB_PORT",  6543);
    public static final String DB_NAME     = env("BAS_DB_NAME",     "postgres");
    public static final String DB_USER     = env("BAS_DB_USER",     "postgres.hpfibvsyorccjihdyfzh");
    public static final String DB_PASSWORD = env("BAS_DB_PASSWORD", "BAS_SWE_9v!Q2m#L7x@P4rT8k$Z1");
    public static final String DB_URL      = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    public static final String JWT_SECRET       = env("BAS_JWT_SECRET","4d7a2e8f1c9b3a6d5e0f7c8b2a4d6e9f1c3b5a7d8e0f2c4b6a8d0e2f4c6b8a");
    public static final long   JWT_EXPIRY_HOURS = 8;
    public static final String SMTP_HOST     = env("BAS_SMTP_HOST",     "smtp.gmail.com");
    public static final int    SMTP_PORT     = envInt("BAS_SMTP_PORT",  587);
    public static final String SMTP_EMAIL    = env("BAS_SMTP_EMAIL",    "shashwat.rajan2005@gmail.com");

    /**
     * AES-128-CBC ciphertext of the SMTP app password, Base64-encoded
     * (IV prepended). Decrypted at runtime using {@link #CRED_KEY}. If
     * {@code BAS_SMTP_PASSWORD} env var is set, it takes precedence and
     * this ciphertext is ignored.
     */
    public static final String SMTP_PASSWORD_ENC =
        "JOKjUtnV97e733PIMVlNFCy8UeH0+T9yjft9L/+wAqXbhFJQ3KSw/jmyfSLj/ysV";

    /**
     * Key used to decrypt {@link #SMTP_PASSWORD_ENC} at runtime.
     * Read from {@code BAS_CRED_KEY} env var; a dev fallback is used when
     * the variable is absent so the demo still works out of the box.
     */
    public static final String CRED_KEY = env("BAS_CRED_KEY", "BAS_AES_demo_key_2026");

    /**
     * Back-compat constant: holds the decoded SMTP password.
     * Populated at class-load time from {@link #SMTP_PASSWORD_ENC} via AES
     * decryption using {@link #CRED_KEY}, unless {@code BAS_SMTP_PASSWORD}
     * env var is set, in which case that value is used verbatim.
     * Existing call sites that referenced this field need not change.
     */
    public static final String SMTP_PASSWORD = decodeSmtpPassword();

    private static String decodeSmtpPassword() {
        String envPw = System.getenv("BAS_SMTP_PASSWORD");
        if (envPw != null && !envPw.isBlank()) return envPw;
        try {
            if (SMTP_PASSWORD_ENC == null || SMTP_PASSWORD_ENC.isBlank()) return "";
            return bas.crypto.AESUtil.decrypt(SMTP_PASSWORD_ENC, CRED_KEY);
        } catch (Exception e) {
            System.err.println("[AppConfig] SMTP password decryption failed; status will show Not Configured. "
                + "(hint: set BAS_CRED_KEY env var to match the key used at encryption time)");
            return "";
        }
    }

    public static final String AES_KEY = env("BAS_AES_KEY", "BAS_AES_k3y_2026_sNu!");
}
