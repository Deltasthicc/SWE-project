package bas.test;

import bas.config.AppConfig;
import bas.db.BookCache;
import bas.db.ConnectionPool;
import bas.db.DatabaseManager;
import bas.model.*;
import bas.util.PrinterUtil;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

@DisplayName("Advanced Scenario Tests (Multi-Item Sales, Boundaries, Config)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestAdvancedScenarios {

    @BeforeAll
    void setup() {
        DatabaseManager.getInstance().initializeDatabase();
        cleanup(); // Clean leftover data from previous runs
    }

    @AfterAll
    void teardown() { cleanup(); }

    private void cleanup() {
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("DELETE FROM sale_items WHERE sale_id LIKE 'SALE-ADV%'");
                s.execute("DELETE FROM sales WHERE sale_id LIKE 'SALE-ADV%'");
                s.execute("DELETE FROM oos_requests WHERE request_id LIKE 'REQ-ADV%'");
                s.execute("DELETE FROM books WHERE isbn='9999999999998'");
            }
            ConnectionPool.getInstance().release(c);
        } catch (Exception ignored) {}
        BookCache.getInstance().invalidate();
    }

    // ═══ APPCONFIG VALIDATION ════════════════════════════════════════════════

    @Test @Order(1) @DisplayName("Config: DB_HOST is non-empty")
    void configDbHost() { assertFalse(AppConfig.DB_HOST.isBlank()); }

    @Test @Order(2) @DisplayName("Config: DB_PORT is valid (1-65535)")
    void configDbPort() { assertTrue(AppConfig.DB_PORT > 0 && AppConfig.DB_PORT <= 65535); }

    @Test @Order(3) @DisplayName("Config: DB_USER is non-empty")
    void configDbUser() { assertFalse(AppConfig.DB_USER.isBlank()); }

    @Test @Order(4) @DisplayName("Config: DB_PASSWORD is non-empty")
    void configDbPassword() { assertFalse(AppConfig.DB_PASSWORD.isBlank()); }

    @Test @Order(5) @DisplayName("Config: JWT_SECRET is at least 32 chars")
    void configJwtSecret() { assertTrue(AppConfig.JWT_SECRET.length() >= 32); }

    @Test @Order(6) @DisplayName("Config: JWT_EXPIRY_HOURS is positive")
    void configJwtExpiry() { assertTrue(AppConfig.JWT_EXPIRY_HOURS > 0); }

    @Test @Order(7) @DisplayName("Config: SMTP_HOST is non-empty")
    void configSmtpHost() { assertFalse(AppConfig.SMTP_HOST.isBlank()); }

    @Test @Order(8) @DisplayName("Config: SMTP_PORT is 587 (STARTTLS)")
    void configSmtpPort() { assertEquals(587, AppConfig.SMTP_PORT); }

    @Test @Order(9) @DisplayName("Config: SMTP_EMAIL contains @")
    void configSmtpEmail() { assertTrue(AppConfig.SMTP_EMAIL.contains("@")); }

    @Test @Order(10) @DisplayName("Config: AES_KEY is non-empty")
    void configAesKey() { assertFalse(AppConfig.AES_KEY.isBlank()); }

    // ═══ MULTI-ITEM SALE ═════════════════════════════════════════════════════

    @Test @Order(20) @DisplayName("MultiSale: 3 different books in one transaction")
    void multiItemSale() {
        SaleRecord sale = new SaleRecord("SALE-ADV-MULTI", "clerk1");
        sale.addItem(new LineItem("9780451524935", "1984", 1, 199.0));
        sale.addItem(new LineItem("9780062315007", "The Alchemist", 2, 310.0));
        sale.addItem(new LineItem("9781982173593", "Atomic Habits", 1, 499.0));

        assertEquals(3, sale.getItems().size());
        assertEquals(199.0 + 620.0 + 499.0, sale.getTotalAmount(), 0.01);

        String receipt = PrinterUtil.buildReceiptString(sale);
        assertTrue(receipt.contains("1984"));
        assertTrue(receipt.contains("Alchemist"));
        assertTrue(receipt.contains("Atomic"));

        boolean ok = DatabaseManager.getInstance().saveSaleAtomically(sale, receipt);
        assertTrue(ok, "Multi-item sale should succeed");

        // Verify line items stored
        List<Object[]> items = DatabaseManager.getInstance().getSaleItems("SALE-ADV-MULTI");
        assertEquals(3, items.size(), "Should have 3 line items in DB");
    }

    @Test @Order(21) @DisplayName("MultiSale: receipt stored correctly for multi-item")
    void multiItemReceipt() {
        String receipt = DatabaseManager.getInstance().getReceiptContent("SALE-ADV-MULTI");
        assertNotNull(receipt);
        assertTrue(receipt.contains("SALE-ADV-MULTI"));
        assertTrue(receipt.contains("TOTAL"));
    }

    // ═══ BOUNDARY: BUY LAST COPY ═════════════════════════════════════════════

    @Test @Order(30) @DisplayName("Boundary: buy exactly the last copy of a book")
    void buyLastCopy() {
        BookCache.getInstance().invalidate();
        // Find a book with exactly 2 copies (The Book Thief)
        Book b = DatabaseManager.getInstance().getByISBN("9780062409850");
        if (b == null || b.getStockCount() < 1) return; // Skip if data changed

        int stock = b.getStockCount();
        SaleRecord sale = new SaleRecord("SALE-ADV-LAST", "clerk1");
        sale.addItem(new LineItem(b.getIsbn(), b.getTitle(), stock, b.getUnitPrice()));

        boolean ok = DatabaseManager.getInstance().saveSaleAtomically(sale, "last-copy-receipt");
        assertTrue(ok, "Buying exactly all remaining copies should succeed");

        BookCache.getInstance().invalidate();
        Book after = DatabaseManager.getInstance().getByISBN(b.getIsbn());
        assertEquals(0, after.getStockCount(), "Stock should be exactly 0");

        // Restore stock
        DatabaseManager.getInstance().addStock(b.getIsbn(), stock, "TEST");
    }

    @Test @Order(31) @DisplayName("Boundary: cannot buy from zero-stock book")
    void buyFromZeroStock() {
        // "It" by Stephen King has 0 stock
        SaleRecord sale = new SaleRecord("SALE-ADV-ZERO", "clerk1");
        sale.addItem(new LineItem("9781501197277", "It", 1, 450.0));
        assertFalse(DatabaseManager.getInstance().saveSaleAtomically(sale, null),
            "Sale from zero-stock book should fail");
    }

    // ═══ DATE RANGE EDGE CASES ═══════════════════════════════════════════════

    @Test @Order(40) @DisplayName("DateRange: same day from and to returns results")
    void sameDayRange() {
        String today = java.time.LocalDate.now().toString();
        List<Object[]> stats = DatabaseManager.getInstance().getSalesStats(today, today);
        assertNotNull(stats);
        // May or may not have sales today — just verify no crash
    }

    @Test @Order(41) @DisplayName("DateRange: reversed dates return empty/zero")
    void reversedDates() {
        double rev = DatabaseManager.getInstance().getTotalRevenue("2025-12-31", "2025-01-01");
        assertEquals(0.0, rev, "Reversed date range should return zero revenue");
    }

    @Test @Order(42) @DisplayName("DateRange: very old dates return zero")
    void ancientDates() {
        double rev = DatabaseManager.getInstance().getTotalRevenue("1900-01-01", "1900-12-31");
        assertEquals(0.0, rev);
    }

    @Test @Order(43) @DisplayName("DateRange: full year range includes all demo sales")
    void fullYearRange() {
        String from = java.time.LocalDate.now().minusDays(365).toString();
        String to = java.time.LocalDate.now().plusDays(1).toString();
        double rev = DatabaseManager.getInstance().getTotalRevenue(from, to);
        assertTrue(rev > 0, "Full year should capture demo sales");
    }

    // ═══ OOS EDGE CASES ══════════════════════════════════════════════════════

    @Test @Order(50) @DisplayName("OOS: request with UNKNOWN ISBN succeeds")
    void oosUnknownISBN() {
        OOSRequest req = new OOSRequest("REQ-ADV-UNK", "UNKNOWN",
            "Requested Book", "Unknown Author", "Unknown Pub", "x@y.com");
        assertTrue(DatabaseManager.getInstance().addOOSRequest(req));
    }

    @Test @Order(51) @DisplayName("OOS: request with very long title succeeds")
    void oosLongTitle() {
        OOSRequest req = new OOSRequest("REQ-ADV-LONG", "UNKNOWN",
            "A".repeat(500), "Author", "Publisher", null);
        assertTrue(DatabaseManager.getInstance().addOOSRequest(req));
    }

    @Test @Order(52) @DisplayName("OOS: getPendingEmails for non-existent ISBN returns empty")
    void oosPendingNonExistent() {
        List<String> emails = DatabaseManager.getInstance().getPendingEmails("0000000000000");
        assertNotNull(emails);
        assertTrue(emails.isEmpty());
    }

    @Test @Order(53) @DisplayName("OOS: markNotified on non-existent ISBN doesn't crash")
    void oosMarkNonExistent() {
        assertDoesNotThrow(() -> DatabaseManager.getInstance().markNotified("0000000000000"));
    }

    // ═══ CACHE CONSISTENCY ═══════════════════════════════════════════════════

    @Test @Order(60) @DisplayName("Cache: addBook invalidates cache and book appears")
    void cacheAfterAdd() {
        Book b = new Book("9999999999998", "Cache Test Book", "Auth", "Pub",
            "Addr", 100.0, "Z-99", 5, 3, 0, 1.0, 1);
        DatabaseManager.getInstance().addBook(b);
        // Cache should be invalidated — next read should include it
        Book found = DatabaseManager.getInstance().getByISBN("9999999999998");
        assertNotNull(found, "Newly added book should be findable via cache");
        assertEquals("Cache Test Book", found.getTitle());

        // Cleanup
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("DELETE FROM books WHERE isbn='9999999999998'");
            }
            ConnectionPool.getInstance().release(c);
            BookCache.getInstance().invalidate();
        } catch (Exception ignored) {}
    }

    @Test @Order(61) @DisplayName("Cache: updateBook reflects in next read")
    void cacheAfterUpdate() {
        BookCache.getInstance().invalidate();
        Book b = DatabaseManager.getInstance().getByISBN("9780451524935"); // 1984
        assertNotNull(b);
        double origPrice = b.getUnitPrice();

        b.setUnitPrice(999.0);
        DatabaseManager.getInstance().updateBook(b);

        Book updated = DatabaseManager.getInstance().getByISBN("9780451524935");
        assertEquals(999.0, updated.getUnitPrice());

        // Restore
        b.setUnitPrice(origPrice);
        DatabaseManager.getInstance().updateBook(b);
    }

    // ═══ SEARCH QUALITY ══════════════════════════════════════════════════════

    @Test @Order(70) @DisplayName("Search: results sorted alphabetically by title")
    void searchSorted() {
        List<Book> books = DatabaseManager.getInstance().getAllBooks();
        for (int i = 1; i < books.size(); i++) {
            assertTrue(books.get(i).getTitle().compareToIgnoreCase(books.get(i-1).getTitle()) >= 0,
                "Books should be sorted: '" + books.get(i-1).getTitle() + "' before '" + books.get(i).getTitle() + "'");
        }
    }

    @Test @Order(71) @DisplayName("Search: partial ISBN doesn't crash (returns empty)")
    void searchPartialISBN() {
        Book b = DatabaseManager.getInstance().getByISBN("978045");
        assertNull(b, "Partial ISBN should not match");
    }

    @Test @Order(72) @DisplayName("Search: author search finds multiple books by same author")
    void searchSameAuthor() {
        List<Book> hoover = DatabaseManager.getInstance().searchByAuthor("Colleen Hoover");
        assertTrue(hoover.size() >= 2, "Colleen Hoover should have 2+ books");
        List<Book> rowling = DatabaseManager.getInstance().searchByAuthor("J.K. Rowling");
        assertTrue(rowling.size() >= 3, "J.K. Rowling should have 3+ books");
    }

    // ═══ LOGS INTEGRITY ══════════════════════════════════════════════════════

    @Test @Order(80) @DisplayName("Logs: INIT log exists from seeding")
    void logsHaveInit() {
        List<Object[]> logs = DatabaseManager.getInstance().getRecentLogs(500);
        assertTrue(logs.stream().anyMatch(l -> "INIT".equals(l[2])),
            "Should have INIT log from database seeding");
    }

    @Test @Order(81) @DisplayName("Logs: SALE logs exist from demo data")
    void logsHaveSales() {
        List<Object[]> logs = DatabaseManager.getInstance().getRecentLogs(500);
        assertTrue(logs.stream().anyMatch(l -> "SALE".equals(l[2])),
            "Should have SALE logs");
    }

    @Test @Order(82) @DisplayName("Logs: each log entry has timestamp")
    void logsHaveTimestamps() {
        List<Object[]> logs = DatabaseManager.getInstance().getRecentLogs(10);
        for (Object[] log : logs) {
            assertNotNull(log[1], "Log entry should have timestamp");
            assertTrue(log[1].toString().length() >= 10, "Timestamp should be meaningful");
        }
    }

    // ═══ RECEIPT FORMAT VALIDATION ════════════════════════════════════════════

    @Test @Order(90) @DisplayName("Receipt: contains separator lines")
    void receiptSeparators() {
        SaleRecord s = new SaleRecord("SALE-ADV-FMT", "clerk1");
        s.addItem(new LineItem("isbn", "Book", 1, 100.0));
        String r = PrinterUtil.buildReceiptString(s);
        assertTrue(r.contains("===="), "Receipt should have === separators");
        assertTrue(r.contains("----"), "Receipt should have --- separators");
    }

    @Test @Order(91) @DisplayName("Receipt: contains 'Thank you' message")
    void receiptThankYou() {
        SaleRecord s = new SaleRecord("SALE-ADV-THX", "clerk1");
        s.addItem(new LineItem("isbn", "Book", 1, 100.0));
        assertTrue(PrinterUtil.buildReceiptString(s).contains("Thank you"));
    }

    @Test @Order(92) @DisplayName("Receipt: contains clerk ID")
    void receiptClerk() {
        SaleRecord s = new SaleRecord("SALE-ADV-CLK", "clerk2");
        s.addItem(new LineItem("isbn", "Book", 1, 100.0));
        assertTrue(PrinterUtil.buildReceiptString(s).contains("clerk2"));
    }

    @Test @Order(93) @DisplayName("Receipt: total matches item subtotals")
    void receiptTotalAccuracy() {
        SaleRecord s = new SaleRecord("SALE-ADV-TOT", "clerk1");
        s.addItem(new LineItem("i1", "Book A", 2, 150.0));
        s.addItem(new LineItem("i2", "Book B", 3, 200.0));
        String r = PrinterUtil.buildReceiptString(s);
        assertTrue(r.contains("900.00"), "Receipt total should be 2*150 + 3*200 = 900.00");
    }

    // ═══ ID GENERATION ═══════════════════════════════════════════════════════

    @Test @Order(95) @DisplayName("ID: newSaleId starts with SALE-")
    void saleIdFormat() {
        String id = DatabaseManager.newSaleId();
        assertTrue(id.startsWith("SALE-"));
        assertEquals(13, id.length()); // SALE- + 8 chars
    }

    @Test @Order(96) @DisplayName("ID: newReqId starts with REQ-")
    void reqIdFormat() {
        String id = DatabaseManager.newReqId();
        assertTrue(id.startsWith("REQ-"));
        assertEquals(12, id.length()); // REQ- + 8 chars
    }

    @Test @Order(97) @DisplayName("ID: generated IDs are unique")
    void idsUnique() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) ids.add(DatabaseManager.newSaleId());
        assertEquals(100, ids.size(), "100 generated sale IDs should all be unique");
    }
}
