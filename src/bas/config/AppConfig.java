package bas.config;

/**
 * Centralised configuration for BAS.
 * Database (Supabase/PostgreSQL), JWT, and SMTP defaults.
 */
public final class AppConfig {

    private AppConfig() {}

    // ── PostgreSQL / Supabase (Session Pooler — IPv4 compatible) ──────────────
    public static final String DB_HOST     = "aws-1-ap-south-1.pooler.supabase.com";
    public static final int    DB_PORT     = 6543;
    public static final String DB_NAME     = "postgres";
    public static final String DB_USER     = "postgres.hpfibvsyorccjihdyfzh";
    public static final String DB_PASSWORD = "BAS_SWE_9v!Q2m#L7x@P4rT8k$Z1";
    public static final String DB_URL      = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;

    // ── JWT ───────────────────────────────────────────────────────────────────
    public static final String JWT_SECRET          = "4d7a2e8f1c9b3a6d5e0f7c8b2a4d6e9f1c3b5a7d8e0f2c4b6a8d0e2f4c6b8a";
    public static final long   JWT_EXPIRY_HOURS    = 8;   // one work-shift

    // ── SMTP (Gmail defaults) ─────────────────────────────────────────────────
    public static final String SMTP_HOST     = "smtp.gmail.com";
    public static final int    SMTP_PORT     = 587;
    public static final String SMTP_EMAIL    = "shashwat.rajan2005@gmail.com";
    public static final String SMTP_PASSWORD = "ykhlutctfczgdkkt";   // App Password (no spaces)

    // ── AES encryption key (for storing sensitive config on disk if needed) ───
    public static final String AES_KEY = "BAS_AES_k3y_2026_sNu!";
}
