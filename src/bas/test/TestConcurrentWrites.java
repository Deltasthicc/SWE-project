package bas.test;

import bas.db.BookCache;
import bas.db.ConnectionPool;
import bas.db.DatabaseManager;
import bas.model.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Concurrent write-race tests — verifies atomicity and consistency under
 * parallel writes. Includes both cloud-DB tests and local-only (no DB) tests.
 */
@DisplayName("Concurrent Write-Race Tests (Atomicity & Consistency)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestConcurrentWrites {

    // Use a seed book that has plenty of stock
    private static final String RACE_ISBN = "9780451524935"; // 1984
    private int originalStock = -1;

    @BeforeAll
    void setup() {
        DatabaseManager.getInstance().initializeDatabase();
        BookCache.getInstance().invalidate();
        Book b = DatabaseManager.getInstance().getByISBN(RACE_ISBN);
        if (b != null) originalStock = b.getStockCount();
        // Ensure we have enough stock for race tests
        ensureStock(RACE_ISBN, 50);
        cleanup();
    }

    @AfterAll
    void teardown() {
        cleanup();
        // Restore original stock
        if (originalStock >= 0) {
            try {
                java.sql.Connection c = ConnectionPool.getInstance().borrow();
                try (java.sql.PreparedStatement ps = c.prepareStatement(
                        "UPDATE books SET stock_count = ? WHERE isbn = ?")) {
                    ps.setInt(1, originalStock);
                    ps.setString(2, RACE_ISBN);
                    ps.executeUpdate();
                }
                ConnectionPool.getInstance().release(c);
                BookCache.getInstance().invalidate();
            } catch (Exception e) {
                System.err.println("[TestConcurrentWrites Cleanup] " + e.getMessage());
            }
        }
    }

    private void cleanup() {
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("DELETE FROM sale_items WHERE sale_id LIKE 'SALE-RACE%'");
                s.execute("DELETE FROM sales WHERE sale_id LIKE 'SALE-RACE%'");
                s.execute("DELETE FROM procurement_orders WHERE order_id LIKE 'PO-%' AND isbn = '" + RACE_ISBN + "'");
                s.execute("DELETE FROM app_logs WHERE actor LIKE 'RACE%'");
            }
            ConnectionPool.getInstance().release(c);
        } catch (Exception e) {
            System.err.println("[TestConcurrentWrites Cleanup] " + e.getMessage());
        }
        BookCache.getInstance().invalidate();
    }

    private void ensureStock(String isbn, int minStock) {
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.PreparedStatement ps = c.prepareStatement(
                    "UPDATE books SET stock_count = GREATEST(stock_count, ?) WHERE isbn = ?")) {
                ps.setInt(1, minStock);
                ps.setString(2, isbn);
                ps.executeUpdate();
            }
            ConnectionPool.getInstance().release(c);
            BookCache.getInstance().invalidate();
        } catch (Exception e) {
            System.err.println("[ensureStock] " + e.getMessage());
        }
    }

    // ═══ CLOUD DB RACE TESTS ═════════════════════════════════════════════════

    @Test @Order(1) @DisplayName("Race: two clerks buy last copy — exactly one succeeds")
    void lastCopyRace() throws Exception {
        // Set stock to exactly 1
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.PreparedStatement ps = c.prepareStatement(
                    "UPDATE books SET stock_count = 1 WHERE isbn = ?")) {
                ps.setString(1, RACE_ISBN);
                ps.executeUpdate();
            }
            ConnectionPool.getInstance().release(c);
            BookCache.getInstance().invalidate();
        } catch (Exception e) { fail("Setup failed: " + e.getMessage()); }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<Boolean> clerk1 = pool.submit(() -> {
            latch.await();
            SaleRecord s = new SaleRecord("SALE-RACE-C1", "clerk1");
            s.addItem(new LineItem(RACE_ISBN, "1984", 1, 199.0));
            return DatabaseManager.getInstance().saveSaleAtomically(s, "race-receipt-1");
        });
        Future<Boolean> clerk2 = pool.submit(() -> {
            latch.await();
            SaleRecord s = new SaleRecord("SALE-RACE-C2", "clerk2");
            s.addItem(new LineItem(RACE_ISBN, "1984", 1, 199.0));
            return DatabaseManager.getInstance().saveSaleAtomically(s, "race-receipt-2");
        });

        latch.countDown(); // Release both threads simultaneously
        boolean r1 = clerk1.get(15, TimeUnit.SECONDS);
        boolean r2 = clerk2.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        // Exactly one should succeed due to FOR UPDATE locking
        assertTrue(r1 ^ r2, "Exactly one clerk should succeed, got clerk1=" + r1 + " clerk2=" + r2);

        // Stock should be 0, not negative
        BookCache.getInstance().invalidate();
        Book after = DatabaseManager.getInstance().getByISBN(RACE_ISBN);
        assertEquals(0, after.getStockCount(), "Stock should be exactly 0 after last-copy race");

        // Restore stock for subsequent tests
        ensureStock(RACE_ISBN, 50);
    }

    @Test @Order(2) @DisplayName("Race: concurrent sales on different ISBNs all succeed")
    void differentISBNsSalesConcurrent() throws Exception {
        String[] isbns = {"9780451524935", "9781982173593", "9780590353427"};
        for (String isbn : isbns) ensureStock(isbn, 30);

        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                SaleRecord s = new SaleRecord("SALE-RACE-DIFF" + n, "clerk1");
                s.addItem(new LineItem(isbns[n], "Book " + n, 1, 100.0));
                return DatabaseManager.getInstance().saveSaleAtomically(s, "receipt");
            }));
        }
        for (Future<Boolean> f : futures) {
            assertTrue(f.get(15, TimeUnit.SECONDS), "Each sale on different ISBN should succeed");
        }
        pool.shutdown();

        // Cleanup extra sales
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("DELETE FROM sale_items WHERE sale_id LIKE 'SALE-RACE-DIFF%'");
                s.execute("DELETE FROM sales WHERE sale_id LIKE 'SALE-RACE-DIFF%'");
            }
            ConnectionPool.getInstance().release(c);
        } catch (Exception ignored) {}
        // Restore stock
        for (String isbn : isbns) ensureStock(isbn, 30);
    }

    @Test @Order(3) @DisplayName("Race: stock remains non-negative after concurrent sales")
    void stockNonNegativeAfterRace() throws Exception {
        // Must SET stock to exactly 3 (ensureStock uses GREATEST which won't lower)
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.PreparedStatement ps = c.prepareStatement(
                    "UPDATE books SET stock_count = 3 WHERE isbn = ?")) {
                ps.setString(1, RACE_ISBN);
                ps.executeUpdate();
            }
            ConnectionPool.getInstance().release(c);
            BookCache.getInstance().invalidate();
        } catch (Exception e) { fail("Setup failed: " + e.getMessage()); }

        // Try 5 concurrent sales of 1 copy each — at most 3 should succeed
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                SaleRecord s = new SaleRecord("SALE-RACE-NEG" + n, "clerk1");
                s.addItem(new LineItem(RACE_ISBN, "1984", 1, 199.0));
                return DatabaseManager.getInstance().saveSaleAtomically(s, "r");
            }));
        }
        int successes = 0;
        for (Future<Boolean> f : futures) {
            if (f.get(15, TimeUnit.SECONDS)) successes++;
        }
        pool.shutdown();

        assertTrue(successes <= 3, "At most 3 of 5 sales should succeed (had 3 stock)");
        BookCache.getInstance().invalidate();
        Book after = DatabaseManager.getInstance().getByISBN(RACE_ISBN);
        assertTrue(after.getStockCount() >= 0, "Stock must never go negative");

        // Cleanup
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("DELETE FROM sale_items WHERE sale_id LIKE 'SALE-RACE-NEG%'");
                s.execute("DELETE FROM sales WHERE sale_id LIKE 'SALE-RACE-NEG%'");
            }
            ConnectionPool.getInstance().release(c);
        } catch (Exception ignored) {}
        ensureStock(RACE_ISBN, 50);
    }

    @Test @Order(4) @DisplayName("Race: concurrent addStock calls accumulate correctly")
    void concurrentAddStock() throws Exception {
        // Set stock to a known value
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.PreparedStatement ps = c.prepareStatement(
                    "UPDATE books SET stock_count = 10 WHERE isbn = ?")) {
                ps.setString(1, RACE_ISBN);
                ps.executeUpdate();
            }
            ConnectionPool.getInstance().release(c);
            BookCache.getInstance().invalidate();
        } catch (Exception e) { fail("Setup failed"); }

        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(pool.submit(() ->
                DatabaseManager.getInstance().addStock(RACE_ISBN, 2, "RACE_TEST")));
        }
        int successes = 0;
        for (Future<Boolean> f : futures) {
            if (f.get(10, TimeUnit.SECONDS)) successes++;
        }
        pool.shutdown();

        assertEquals(5, successes, "All 5 addStock calls should succeed");
        BookCache.getInstance().invalidate();
        Book after = DatabaseManager.getInstance().getByISBN(RACE_ISBN);
        assertEquals(20, after.getStockCount(), "Stock should be 10 + 5*2 = 20");

        ensureStock(RACE_ISBN, 50);
    }

    // ═══ LOCAL-ONLY TESTS (no cloud DB needed) ═══════════════════════════════

    @Test @Order(10) @DisplayName("Local: concurrent SaleRecord builds don't interfere")
    void localConcurrentSaleBuilds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<SaleRecord>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                SaleRecord s = new SaleRecord("SALE-LOCAL-" + n, "clerk" + (n % 2 + 1));
                s.addItem(new LineItem("isbn-" + n, "Book " + n, n + 1, 100.0 + n));
                return s;
            }));
        }
        Set<String> ids = new HashSet<>();
        for (Future<SaleRecord> f : futures) {
            SaleRecord s = f.get(5, TimeUnit.SECONDS);
            assertTrue(ids.add(s.getSaleId()), "Sale IDs should be unique");
            assertEquals(1, s.getItems().size());
        }
        pool.shutdown();
        assertEquals(10, ids.size());
    }

    @Test @Order(11) @DisplayName("Local: concurrent ISBN validations are thread-safe")
    void localConcurrentISBNValidation() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        String[] isbns = {"9780451524935", "9781982173593", "0451524934", "080442957X", "invalid"};
        boolean[] expected = {true, true, true, true, false};
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            final int idx = i % 5;
            futures.add(pool.submit(() -> bas.util.ISBNValidator.isValid(isbns[idx])));
        }
        for (int i = 0; i < 50; i++) {
            assertEquals(expected[i % 5], futures.get(i).get(5, TimeUnit.SECONDS));
        }
        pool.shutdown();
    }

    @Test @Order(12) @DisplayName("Local: concurrent email validations are thread-safe")
    void localConcurrentEmailValidation() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        String[] emails = {"test@example.com", "invalid", "a@b.co", "", "user@domain.org"};
        boolean[] expected = {true, false, true, false, true};
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            final int idx = i % 5;
            futures.add(pool.submit(() -> bas.util.EmailValidator.isValid(emails[idx])));
        }
        for (int i = 0; i < 50; i++) {
            assertEquals(expected[i % 5], futures.get(i).get(5, TimeUnit.SECONDS));
        }
        pool.shutdown();
    }

    @Test @Order(13) @DisplayName("Local: concurrent receipt generation is thread-safe")
    void localConcurrentReceiptGen() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                SaleRecord s = new SaleRecord("SALE-REC-" + n, "clerk1");
                s.addItem(new LineItem("isbn", "Book " + n, n + 1, 99.0));
                return bas.util.PrinterUtil.buildReceiptString(s);
            }));
        }
        for (int i = 0; i < 20; i++) {
            String receipt = futures.get(i).get(5, TimeUnit.SECONDS);
            assertNotNull(receipt);
            assertTrue(receipt.contains("SALE-REC-" + i));
            assertTrue(receipt.contains("TOTAL"));
        }
        pool.shutdown();
    }

    @Test @Order(14) @DisplayName("Local: concurrent hash computations are deterministic")
    void localConcurrentHashing() throws Exception {
        String password = "testPassword123!";
        String salt = DatabaseManager.generateSalt();
        String expectedHash = DatabaseManager.hash(password, salt);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(() -> DatabaseManager.hash(password, salt)));
        }
        for (Future<String> f : futures) {
            assertEquals(expectedHash, f.get(5, TimeUnit.SECONDS));
        }
        pool.shutdown();
    }

    @Test @Order(15) @DisplayName("Local: concurrent salt generations are all unique")
    void localConcurrentSaltGen() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(DatabaseManager::generateSalt));
        }
        Set<String> salts = ConcurrentHashMap.newKeySet();
        for (Future<String> f : futures) {
            String s = f.get(5, TimeUnit.SECONDS);
            assertNotNull(s);
            assertEquals(32, s.length(), "Salt should be 32 hex chars (16 bytes)");
            assertTrue(salts.add(s), "All salts should be unique");
        }
        pool.shutdown();
    }

    @Test @Order(16) @DisplayName("Local: concurrent AES encrypt/decrypt round-trips")
    void localConcurrentAES() throws Exception {
        String key = bas.config.AppConfig.AES_KEY;
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            final String plaintext = "Secret message #" + i;
            futures.add(pool.submit(() -> {
                String enc = bas.crypto.AESUtil.encrypt(plaintext, key);
                String dec = bas.crypto.AESUtil.decrypt(enc, key);
                return plaintext.equals(dec);
            }));
        }
        for (Future<Boolean> f : futures) {
            assertTrue(f.get(5, TimeUnit.SECONDS), "AES round-trip must succeed");
        }
        pool.shutdown();
    }
}
