package bas.test;

import bas.db.ConnectionPool;
import bas.db.BookCache;
import bas.db.DatabaseManager;
import bas.model.Book;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

@DisplayName("Connection Pool & Book Cache Tests (Performance Layer)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestPoolAndCache {

    @BeforeAll
    static void init() {
        DatabaseManager.getInstance().initializeDatabase();
    }

    // ═══ CONNECTION POOL ═════════════════════════════════════════════════════

    @Test @Order(1) @DisplayName("Pool: borrow returns valid connection")
    void poolBorrow() throws SQLException {
        Connection c = ConnectionPool.getInstance().borrow();
        assertNotNull(c);
        assertFalse(c.isClosed());
        ConnectionPool.getInstance().release(c);
    }

    @Test @Order(2) @DisplayName("Pool: released connection is reusable")
    void poolReuse() throws SQLException {
        Connection c1 = ConnectionPool.getInstance().borrow();
        ConnectionPool.getInstance().release(c1);
        Connection c2 = ConnectionPool.getInstance().borrow();
        assertNotNull(c2);
        assertFalse(c2.isClosed());
        // Might be the same physical connection
        ConnectionPool.getInstance().release(c2);
    }

    @Test @Order(3) @DisplayName("Pool: multiple concurrent borrows succeed")
    void poolConcurrent() throws SQLException {
        List<Connection> conns = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Connection c = ConnectionPool.getInstance().borrow();
            assertNotNull(c);
            conns.add(c);
        }
        for (Connection c : conns) {
            ConnectionPool.getInstance().release(c);
        }
    }

    @Test @Order(4) @DisplayName("Pool: pooled borrow completes within reasonable time")
    void poolPerformance() throws SQLException {
        // Warm up — create and return a connection
        Connection warmup = ConnectionPool.getInstance().borrow();
        ConnectionPool.getInstance().release(warmup);

        // Measure pooled borrow (should be fast — reusing existing)
        long start = System.nanoTime();
        Connection c = ConnectionPool.getInstance().borrow();
        long timeMs = (System.nanoTime() - start) / 1_000_000;
        ConnectionPool.getInstance().release(c);

        System.out.println("  Pooled borrow: " + timeMs + "ms");
        // Pooled borrow should complete within 3 seconds even with network jitter
        assertTrue(timeMs < 3000, "Pooled borrow took " + timeMs + "ms, should be < 3000ms");
    }

    @Test @Order(5) @DisplayName("Pool: release null connection doesn't throw")
    void poolReleaseNull() {
        assertDoesNotThrow(() -> ConnectionPool.getInstance().release(null));
    }

    // ═══ BOOK CACHE ═════════════════════════════════════════════════════════

    @Test @Order(10) @DisplayName("Cache: getAllBooks returns books")
    void cacheGetAll() {
        List<Book> books = BookCache.getInstance().getAllBooks();
        assertNotNull(books);
        assertFalse(books.isEmpty());
    }

    @Test @Order(11) @DisplayName("Cache: second call is faster (cached)")
    void cacheSpeed() {
        BookCache.getInstance().invalidate();

        long start1 = System.nanoTime();
        BookCache.getInstance().getAllBooks();
        long time1 = System.nanoTime() - start1;

        long start2 = System.nanoTime();
        BookCache.getInstance().getAllBooks();
        long time2 = System.nanoTime() - start2;

        System.out.println("  First load: " + (time1/1_000_000) + "ms, Cached: " + (time2/1_000_000) + "ms");
        assertTrue(time2 < time1, "Cached read should be faster");
        assertTrue(time2 < 5_000_000, "Cached read should be < 5ms, was " + (time2/1_000_000) + "ms");
    }

    @Test @Order(12) @DisplayName("Cache: searchByTitle returns correct results")
    void cacheSearchTitle() {
        List<Book> results = BookCache.getInstance().searchByTitle("Harry Potter");
        assertTrue(results.size() >= 3, "Should find at least 3 Harry Potter books");
    }

    @Test @Order(13) @DisplayName("Cache: searchByAuthor returns correct results")
    void cacheSearchAuthor() {
        List<Book> results = BookCache.getInstance().searchByAuthor("Colleen Hoover");
        assertTrue(results.size() >= 2);
    }

    @Test @Order(14) @DisplayName("Cache: getByISBN returns correct book")
    void cacheGetByISBN() {
        Book b = BookCache.getInstance().getByISBN("9780451524935");
        assertNotNull(b);
        assertEquals("1984", b.getTitle());
    }

    @Test @Order(15) @DisplayName("Cache: getByISBN returns null for missing")
    void cacheGetByISBNMissing() {
        assertNull(BookCache.getInstance().getByISBN("0000000000000"));
    }

    @Test @Order(16) @DisplayName("Cache: getBooksNeedingRestock returns sorted results")
    void cacheRestock() {
        List<Book> restock = BookCache.getInstance().getBooksNeedingRestock();
        assertFalse(restock.isEmpty());
        // Should be sorted by stock count ascending
        for (int i = 1; i < restock.size(); i++) {
            assertTrue(restock.get(i).getStockCount() >= restock.get(i-1).getStockCount(),
                "Should be sorted by stock ascending");
        }
    }

    @Test @Order(17) @DisplayName("Cache: invalidate forces re-fetch on next call")
    void cacheInvalidate() {
        // Get cached
        BookCache.getInstance().getAllBooks();
        // Invalidate
        BookCache.getInstance().invalidate();
        // Next call should still work (re-fetches from DB)
        List<Book> books = BookCache.getInstance().getAllBooks();
        assertFalse(books.isEmpty());
    }

    @Test @Order(18) @DisplayName("Cache: search is case-insensitive")
    void cacheCaseInsensitive() {
        List<Book> r1 = BookCache.getInstance().searchByTitle("ATOMIC HABITS");
        List<Book> r2 = BookCache.getInstance().searchByTitle("atomic habits");
        assertEquals(r1.size(), r2.size());
    }

    @Test @Order(19) @DisplayName("Cache: refresh returns fresh data")
    void cacheRefresh() {
        List<Book> books = BookCache.getInstance().refresh();
        assertNotNull(books);
        assertFalse(books.isEmpty());
    }
}
