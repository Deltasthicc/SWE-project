package bas.db;

import bas.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Lightweight JDBC connection pool for Supabase/PostgreSQL.
 *
 * Instead of opening a new TCP+SSL connection per query (~300-500ms),
 * this pool maintains a set of live, reusable connections.
 *
 * Thread-safe via BlockingQueue.
 */
public final class ConnectionPool {

    private static final int POOL_SIZE = 5;
    private static final long MAX_IDLE_MS = 2 * 60 * 1000; // 2 minutes (PgBouncer kills idle faster)

    private static ConnectionPool instance;
    private final BlockingQueue<PooledConnection> pool;
    private final String url;
    private final Properties props;

    private ConnectionPool() {
        pool = new ArrayBlockingQueue<>(POOL_SIZE);
        url = "jdbc:postgresql://" + AppConfig.DB_HOST + ":" + AppConfig.DB_PORT + "/" + AppConfig.DB_NAME;
        props = new Properties();
        props.setProperty("user",             AppConfig.DB_USER);
        props.setProperty("password",         AppConfig.DB_PASSWORD);
        props.setProperty("sslmode",          "require");
        props.setProperty("connectTimeout",   "15");
        props.setProperty("socketTimeout",    "30");
        props.setProperty("prepareThreshold", "0");  // PgBouncer compatibility
    }

    public static synchronized ConnectionPool getInstance() {
        if (instance == null) instance = new ConnectionPool();
        return instance;
    }

    /**
     * Borrow a connection from the pool.
     * Returns an existing idle connection if available, or creates a new one.
     */
    public Connection borrow() throws SQLException {
        // Try to get an existing connection
        PooledConnection pc = pool.poll();
        while (pc != null) {
            if (isAlive(pc)) {
                return pc.conn;
            }
            // Connection is dead or too old — discard it
            closeQuietly(pc.conn);
            pc = pool.poll();
        }
        // No pooled connections available — create fresh
        return createConnection();
    }

    /**
     * Return a connection to the pool for reuse.
     * If the pool is full, the connection is closed instead.
     */
    public void release(Connection conn) {
        if (conn == null) return;
        try {
            if (conn.isClosed()) return;
            // Reset connection state
            if (!conn.getAutoCommit()) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                conn.setAutoCommit(true);
            }
            PooledConnection pc = new PooledConnection(conn, System.currentTimeMillis());
            if (!pool.offer(pc)) {
                // Pool is full — close this connection
                closeQuietly(conn);
            }
        } catch (SQLException e) {
            closeQuietly(conn);
        }
    }

    /** Create a raw JDBC connection. */
    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(url, props);
    }

    /** Check if a pooled connection is still usable. */
    private boolean isAlive(PooledConnection pc) {
        try {
            if (pc.conn.isClosed()) return false;
            if (System.currentTimeMillis() - pc.createdAt > MAX_IDLE_MS) return false;
            // Quick validation — MUST use return value
            return pc.conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private static void closeQuietly(Connection c) {
        try { if (c != null && !c.isClosed()) c.close(); } catch (SQLException ignored) {}
    }

    /** Shut down the pool, closing all connections. */
    public void shutdown() {
        PooledConnection pc;
        while ((pc = pool.poll()) != null) closeQuietly(pc.conn);
        System.out.println("[Pool] Connection pool shut down.");
    }

    // ── Inner wrapper ─────────────────────────────────────────────────────────

    private static class PooledConnection {
        final Connection conn;
        final long createdAt;
        PooledConnection(Connection c, long t) { conn = c; createdAt = t; }
    }
}
