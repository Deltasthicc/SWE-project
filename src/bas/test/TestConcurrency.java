package bas.test;

import bas.db.BookCache;
import bas.db.ConnectionPool;
import bas.db.DatabaseManager;
import bas.model.*;
import bas.auth.JWTUtil;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * Concurrency and stress tests — thread safety, race conditions, parallel operations.
 */
@DisplayName("Concurrency & Stress Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestConcurrency {

    @BeforeAll static void init() { DatabaseManager.getInstance().initializeDatabase(); }

    // ═══ PARALLEL SEARCHES ══════════════════════════════════════════════════

    @Test @Order(1) @DisplayName("Concurrent: 10 parallel title searches complete without error")
    void parallelTitleSearches() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<List<Book>>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> DatabaseManager.getInstance().searchByTitle("the")));
        }
        for (Future<List<Book>> f : futures) {
            List<Book> result = f.get(10, TimeUnit.SECONDS);
            assertNotNull(result);
        }
        pool.shutdown();
    }

    @Test @Order(2) @DisplayName("Concurrent: 10 parallel author searches complete without error")
    void parallelAuthorSearches() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<List<Book>>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> DatabaseManager.getInstance().searchByAuthor("a")));
        }
        for (Future<List<Book>> f : futures) {
            assertNotNull(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();
    }

    @Test @Order(3) @DisplayName("Concurrent: 10 parallel ISBN lookups complete without error")
    void parallelISBNLookups() throws Exception {
        String[] isbns = {"9780451524935","9781982173593","9780590353427","9780062315007","9780439023481"};
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Book>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String isbn = isbns[i % isbns.length];
            futures.add(pool.submit(() -> DatabaseManager.getInstance().getByISBN(isbn)));
        }
        for (Future<Book> f : futures) {
            assertNotNull(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();
    }

    // ═══ PARALLEL CACHE ACCESS ═══════════════════════════════════════════════

    @Test @Order(10) @DisplayName("Concurrent: cache reads from multiple threads")
    void parallelCacheReads() throws Exception {
        BookCache.getInstance().refresh(); // Pre-warm
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<List<Book>>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(() -> BookCache.getInstance().getAllBooks()));
        }
        for (Future<List<Book>> f : futures) {
            List<Book> result = f.get(10, TimeUnit.SECONDS);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
        pool.shutdown();
    }

    @Test @Order(11) @DisplayName("Concurrent: cache search from multiple threads")
    void parallelCacheSearches() throws Exception {
        BookCache.getInstance().refresh();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<List<Book>>> futures = new ArrayList<>();
        String[] queries = {"Harry", "Atomic", "King", "Orwell", "Tolkien"};
        for (int i = 0; i < 15; i++) {
            String q = queries[i % queries.length];
            futures.add(pool.submit(() -> BookCache.getInstance().searchByTitle(q)));
        }
        for (Future<List<Book>> f : futures) {
            assertNotNull(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();
    }

    @Test @Order(12) @DisplayName("Concurrent: invalidate + read doesn't crash")
    void cacheInvalidateWhileReading() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            if (i % 3 == 0) {
                futures.add(pool.submit(() -> BookCache.getInstance().invalidate()));
            } else {
                futures.add(pool.submit(() -> BookCache.getInstance().getAllBooks()));
            }
        }
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(15, TimeUnit.SECONDS));
        }
        pool.shutdown();
    }

    // ═══ PARALLEL CONNECTION POOL ════════════════════════════════════════════

    @Test @Order(20) @DisplayName("Concurrent: 10 parallel connection borrow/release cycles")
    void parallelPoolCycles() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> {
                java.sql.Connection c = ConnectionPool.getInstance().borrow();
                boolean valid = c != null && !c.isClosed();
                ConnectionPool.getInstance().release(c);
                return valid;
            }));
        }
        for (Future<Boolean> f : futures) {
            assertTrue(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();
    }

    // ═══ PARALLEL JWT GENERATION ═════════════════════════════════════════════

    @Test @Order(30) @DisplayName("Concurrent: 20 parallel JWT generations are all unique")
    void parallelJWTGeneration() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int n = i;
            futures.add(pool.submit(() -> JWTUtil.generateToken("user" + n, "Name" + n, "CLERK")));
        }
        List<String> tokens = new ArrayList<>();
        for (Future<String> f : futures) {
            String token = f.get(5, TimeUnit.SECONDS);
            assertNotNull(token);
            tokens.add(token);
        }
        // All tokens should be unique (different subjects + timestamps)
        assertEquals(20, new java.util.HashSet<>(tokens).size(), "All 20 JWTs should be unique");
        pool.shutdown();
    }

    @Test @Order(31) @DisplayName("Concurrent: parallel JWT validation")
    void parallelJWTValidation() throws Exception {
        // Generate tokens first
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tokens.add(JWTUtil.generateToken("user" + i, "Name", "OWNER"));
        }
        // Validate in parallel
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<String>> futures = new ArrayList<>();
        for (String token : tokens) {
            futures.add(pool.submit(() -> JWTUtil.validateToken(token)));
        }
        for (Future<String> f : futures) {
            assertNotNull(f.get(5, TimeUnit.SECONDS), "All tokens should validate");
        }
        pool.shutdown();
    }

    // ═══ PARALLEL LOGGING ════════════════════════════════════════════════════

    @Test @Order(40) @DisplayName("Concurrent: 10 parallel log writes don't interfere")
    void parallelLogWrites() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int n = i;
            futures.add(pool.submit(() ->
                DatabaseManager.getInstance().addLog("CONC_TEST_" + n, "TEST", "Concurrent log " + n)));
        }
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS); // No exception = success
        }
        pool.shutdown();
    }

    // ═══ PARALLEL MIXED OPERATIONS ═══════════════════════════════════════════

    @Test @Order(50) @DisplayName("Concurrent: mixed read operations (search + getAll + getByISBN)")
    void parallelMixedReads() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            switch (i % 3) {
                case 0 -> futures.add(pool.submit(() -> DatabaseManager.getInstance().searchByTitle("Harry")));
                case 1 -> futures.add(pool.submit(() -> DatabaseManager.getInstance().getAllBooks()));
                case 2 -> futures.add(pool.submit(() -> DatabaseManager.getInstance().getByISBN("9780451524935")));
            }
        }
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(15, TimeUnit.SECONDS));
        }
        pool.shutdown();
    }

    // ═══ STRESS: RAPID SEQUENTIAL OPERATIONS ═════════════════════════════════

    @Test @Order(60) @DisplayName("Stress: 50 rapid sequential searches complete")
    void stressSequentialSearches() {
        for (int i = 0; i < 50; i++) {
            List<Book> r = DatabaseManager.getInstance().searchByTitle("a");
            assertNotNull(r);
        }
    }

    @Test @Order(61) @DisplayName("Stress: 50 rapid sequential cache hits")
    void stressCacheHits() {
        BookCache.getInstance().refresh();
        for (int i = 0; i < 50; i++) {
            assertFalse(BookCache.getInstance().getAllBooks().isEmpty());
        }
    }

    @Test @Order(62) @DisplayName("Stress: 20 rapid sequential ISBN lookups")
    void stressISBNLookups() {
        for (int i = 0; i < 20; i++) {
            assertNotNull(DatabaseManager.getInstance().getByISBN("9780451524935"));
        }
    }

    @Test @Order(63) @DisplayName("Stress: 100 rapid sequential hash computations")
    void stressHashing() {
        for (int i = 0; i < 100; i++) {
            String h = DatabaseManager.hash("password" + i);
            assertNotNull(h);
            assertEquals(64, h.length());
        }
    }

    @Test @Order(64) @DisplayName("Stress: 100 rapid sequential JWT generations")
    void stressJWTGeneration() {
        for (int i = 0; i < 100; i++) {
            String token = JWTUtil.generateToken("user", "Name", "CLERK");
            assertNotNull(JWTUtil.validateToken(token));
        }
    }

    // ═══ PARALLEL SALE ID UNIQUENESS ═════════════════════════════════════════

    @Test @Order(70) @DisplayName("Concurrent: 50 parallel sale ID generations are all unique")
    void parallelSaleIds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(DatabaseManager::newSaleId));
        }
        java.util.Set<String> ids = ConcurrentHashMap.newKeySet();
        for (Future<String> f : futures) {
            assertTrue(ids.add(f.get(5, TimeUnit.SECONDS)), "Generated sale IDs must be unique");
        }
        assertEquals(50, ids.size());
        pool.shutdown();
    }

    @Test @Order(71) @DisplayName("Concurrent: 50 parallel request ID generations are all unique")
    void parallelReqIds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(DatabaseManager::newReqId));
        }
        java.util.Set<String> ids = ConcurrentHashMap.newKeySet();
        for (Future<String> f : futures) {
            assertTrue(ids.add(f.get(5, TimeUnit.SECONDS)), "Generated req IDs must be unique");
        }
        assertEquals(50, ids.size());
        pool.shutdown();
    }
}
