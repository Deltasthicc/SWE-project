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
    public static final String SMTP_PASSWORD = env("BAS_SMTP_PASSWORD", "ykhlutctfczgdkkt");
    public static final String AES_KEY = env("BAS_AES_KEY", "BAS_AES_k3y_2026_sNu!");
}
