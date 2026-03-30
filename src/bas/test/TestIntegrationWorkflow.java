package bas.test;

import bas.auth.JWTUtil;
import bas.auth.SessionManager;
import bas.db.BookCache;
import bas.db.ConnectionPool;
import bas.db.DatabaseManager;
import bas.model.*;
import bas.util.ISBNValidator;
import bas.util.PrinterUtil;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

@DisplayName("Integration Workflow Tests (End-to-End Scenarios)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestIntegrationWorkflow {

    private static final String INT_SALE_ID = "SALE-INT-001";

    @BeforeAll
    void setup() {
        DatabaseManager.getInstance().initializeDatabase();
    }

    @AfterAll
    void cleanup() {
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("DELETE FROM sale_items WHERE sale_id LIKE 'SALE-INT%'");
                s.execute("DELETE FROM sales WHERE sale_id LIKE 'SALE-INT%'");
                s.execute("DELETE FROM oos_requests WHERE request_id LIKE 'REQ-INT%'");
            }
            ConnectionPool.getInstance().release(c);
        } catch (Exception ignored) {}
        SessionManager.getInstance().logout();
        BookCache.getInstance().invalidate();
    }

    // ═══ WORKFLOW 1: Complete POS Transaction ════════════════════════════════
    // Clerk logs in → searches book → validates ISBN → adds to cart →
    // confirms sale → stock decremented → receipt stored

    @Test @Order(1) @DisplayName("W1.1: Clerk authenticates successfully")
    void w1_login() {
        User clerk = DatabaseManager.getInstance().authenticate("clerk1", "clerk123");
        assertNotNull(clerk, "Clerk should authenticate");
        assertEquals(User.Role.CLERK, clerk.getRole());
        SessionManager.getInstance().login(clerk);
        assertTrue(SessionManager.getInstance().isAuthenticated());
    }

    @Test @Order(2) @DisplayName("W1.2: Clerk searches for a book by title")
    void w1_search() {
        List<Book> results = DatabaseManager.getInstance().searchByTitle("Atomic Habits");
        assertFalse(results.isEmpty());
        Book b = results.get(0);
        assertEquals("9781982173593", b.getIsbn());
        assertTrue(b.isInStock(), "Atomic Habits should be in stock");
    }

    @Test @Order(3) @DisplayName("W1.3: ISBN validation passes for the found book")
    void w1_validateISBN() {
        assertTrue(ISBNValidator.isValid("9781982173593"));
        assertNull(ISBNValidator.getValidationError("9781982173593"));
    }

    @Test @Order(4) @DisplayName("W1.4: Session validated before sale (JWT check)")
    void w1_sessionCheck() {
        assertTrue(SessionManager.getInstance().isAuthenticated());
        assertTrue(SessionManager.getInstance().hasRole(
            User.Role.CLERK, User.Role.MANAGER, User.Role.OWNER));
    }

    @Test @Order(5) @DisplayName("W1.5: Build sale with items and verify total")
    void w1_buildSale() {
        Book b = DatabaseManager.getInstance().getByISBN("9781982173593");
        assertNotNull(b);

        SaleRecord sale = new SaleRecord(INT_SALE_ID, "clerk1");
        sale.addItem(new LineItem(b.getIsbn(), b.getTitle(), 2, b.getUnitPrice()));
        assertEquals(2 * b.getUnitPrice(), sale.getTotalAmount(), 0.001);
    }

    @Test @Order(6) @DisplayName("W1.6: Atomic sale commits, stock decrements, receipt stored")
    void w1_confirmSale() {
        BookCache.getInstance().invalidate();
        Book before = DatabaseManager.getInstance().getByISBN("9781982173593");
        int stockBefore = before.getStockCount();

        SaleRecord sale = new SaleRecord(INT_SALE_ID, "clerk1");
        sale.addItem(new LineItem(before.getIsbn(), before.getTitle(), 1, before.getUnitPrice()));
        String receipt = PrinterUtil.buildReceiptString(sale);

        assertTrue(DatabaseManager.getInstance().saveSaleAtomically(sale, receipt));

        BookCache.getInstance().invalidate();
        Book after = DatabaseManager.getInstance().getByISBN("9781982173593");
        assertEquals(stockBefore - 1, after.getStockCount(), "Stock should decrement by 1");

        String stored = DatabaseManager.getInstance().getReceiptContent(INT_SALE_ID);
        assertNotNull(stored, "Receipt should be stored in DB");
        assertTrue(stored.contains(INT_SALE_ID));
    }

    @Test @Order(7) @DisplayName("W1.7: Transaction appears in history")
    void w1_history() {
        List<Object[]> txns = DatabaseManager.getInstance().getTransactionHistory(100);
        assertTrue(txns.stream().anyMatch(t -> INT_SALE_ID.equals(t[0])));
    }

    // ═══ WORKFLOW 2: Customer OOS Request Flow ══════════════════════════════
    // Customer searches → book out of stock → submits OOS request with email →
    // request recorded → email in pending list

    @Test @Order(10) @DisplayName("W2.1: Customer searches for out-of-stock book")
    void w2_searchOOS() {
        List<Book> results = DatabaseManager.getInstance().searchByTitle("Circe");
        assertFalse(results.isEmpty());
        Book b = results.get(0);
        assertEquals(0, b.getStockCount(), "Circe should be out of stock");
        assertFalse(b.isInStock());
    }

    @Test @Order(11) @DisplayName("W2.2: Customer submits OOS request with email")
    void w2_submitRequest() {
        OOSRequest req = new OOSRequest("REQ-INT-001", "9780062797155",
            "Circe", "Madeline Miller", "Little Brown & Co.", "customer@test.com");
        assertTrue(DatabaseManager.getInstance().addOOSRequest(req));
    }

    @Test @Order(12) @DisplayName("W2.3: Request appears in OOS list")
    void w2_requestInList() {
        List<OOSRequest> reqs = DatabaseManager.getInstance().getAllOOSRequests();
        assertTrue(reqs.stream().anyMatch(r -> "REQ-INT-001".equals(r.getRequestId())));
    }

    @Test @Order(13) @DisplayName("W2.4: Email is in pending list for that ISBN")
    void w2_emailPending() {
        List<String> emails = DatabaseManager.getInstance().getPendingEmails("9780062797155");
        assertTrue(emails.contains("customer@test.com"));
    }

    // ═══ WORKFLOW 3: Owner Reports ══════════════════════════════════════════
    // Owner logs in → generates sales stats → checks procurement → reviews logs

    @Test @Order(20) @DisplayName("W3.1: Owner authenticates and gets JWT")
    void w3_ownerLogin() {
        User owner = DatabaseManager.getInstance().authenticate("owner1", "owner123");
        assertNotNull(owner);
        SessionManager.getInstance().login(owner);
        assertTrue(SessionManager.getInstance().hasRole(User.Role.OWNER));
        assertNotNull(SessionManager.getInstance().getToken());
    }

    @Test @Order(21) @DisplayName("W3.2: Owner generates sales report")
    void w3_salesReport() {
        String from = java.time.LocalDate.now().minusDays(30).toString();
        String to = java.time.LocalDate.now().toString();
        List<Object[]> stats = DatabaseManager.getInstance().getSalesStats(from, to);
        assertFalse(stats.isEmpty());
        double rev = DatabaseManager.getInstance().getTotalRevenue(from, to);
        assertTrue(rev > 0);
    }

    @Test @Order(22) @DisplayName("W3.3: Owner checks procurement needs")
    void w3_procurement() {
        List<Book> restock = DatabaseManager.getInstance().getBooksNeedingRestock();
        // Should include out-of-stock books
        assertTrue(restock.stream().anyMatch(b -> b.getStockCount() == 0));
        // Each book should have a valid procurement qty
        for (Book b : restock) {
            assertTrue(b.getRequiredProcurementQty() >= 0);
        }
    }

    @Test @Order(23) @DisplayName("W3.4: Owner reviews activity logs")
    void w3_logs() {
        List<Object[]> logs = DatabaseManager.getInstance().getRecentLogs(50);
        assertFalse(logs.isEmpty());
        // Should contain our test sale log
        assertTrue(logs.stream().anyMatch(l -> {
            String msg = l[4] != null ? l[4].toString() : "";
            return msg.contains(INT_SALE_ID);
        }));
    }

    // ═══ WORKFLOW 4: Manager Stock Update Triggers Notification ══════════════

    @Test @Order(30) @DisplayName("W4.1: Manager logs in")
    void w4_managerLogin() {
        User mgr = DatabaseManager.getInstance().authenticate("manager1", "mgr123");
        assertNotNull(mgr);
        assertEquals(User.Role.MANAGER, mgr.getRole());
    }

    @Test @Order(31) @DisplayName("W4.2: Manager adds stock to out-of-stock book")
    void w4_addStock() {
        // Circe is out of stock
        BookCache.getInstance().invalidate();
        Book before = DatabaseManager.getInstance().getByISBN("9780062797155");
        assertEquals(0, before.getStockCount());

        assertTrue(DatabaseManager.getInstance().addStock("9780062797155", 10, "manager1"));

        BookCache.getInstance().invalidate();
        Book after = DatabaseManager.getInstance().getByISBN("9780062797155");
        assertEquals(10, after.getStockCount());
    }

    @Test @Order(32) @DisplayName("W4.3: Pending emails exist for restocked book")
    void w4_pendingEmails() {
        List<String> emails = DatabaseManager.getInstance().getPendingEmails("9780062797155");
        assertFalse(emails.isEmpty(), "Should have pending emails after restock");
    }

    @Test @Order(33) @DisplayName("W4.4: Mark notified clears pending status")
    void w4_markNotified() {
        DatabaseManager.getInstance().markNotified("9780062797155");
        List<String> emails = DatabaseManager.getInstance().getPendingEmails("9780062797155");
        assertTrue(emails.isEmpty() || !emails.contains("customer@test.com"),
            "After marking notified, test email should not be pending");
    }

    // Restore stock to 0 for other tests
    @Test @Order(34) @DisplayName("W4.5: Cleanup — restore Circe to original stock")
    void w4_cleanup() {
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.PreparedStatement ps = c.prepareStatement(
                    "UPDATE books SET stock_count=0 WHERE isbn='9780062797155'")) {
                ps.executeUpdate();
            }
            ConnectionPool.getInstance().release(c);
            BookCache.getInstance().invalidate();
        } catch (Exception ignored) {}
    }

    // ═══ WORKFLOW 5: Role-Based Access Control ══════════════════════════════

    @Test @Order(40) @DisplayName("W5.1: Clerk cannot access OWNER-only features")
    void w5_clerkRestricted() {
        User clerk = new User("clerk1", "Arjun", "hash", User.Role.CLERK);
        SessionManager.getInstance().login(clerk);
        assertFalse(SessionManager.getInstance().hasRole(User.Role.OWNER));
        assertFalse(SessionManager.getInstance().hasRole(User.Role.MANAGER));
        assertTrue(SessionManager.getInstance().hasRole(User.Role.CLERK));
    }

    @Test @Order(41) @DisplayName("W5.2: Manager can access MANAGER but not OWNER features")
    void w5_managerAccess() {
        User mgr = new User("mgr1", "Priya", "hash", User.Role.MANAGER);
        SessionManager.getInstance().login(mgr);
        assertTrue(SessionManager.getInstance().hasRole(User.Role.MANAGER, User.Role.OWNER));
        assertFalse(SessionManager.getInstance().hasRole(User.Role.OWNER));
    }

    @Test @Order(42) @DisplayName("W5.3: Owner has access to everything")
    void w5_ownerFullAccess() {
        User owner = new User("owner1", "Ravi", "hash", User.Role.OWNER);
        SessionManager.getInstance().login(owner);
        assertTrue(SessionManager.getInstance().hasRole(User.Role.OWNER));
        assertTrue(SessionManager.getInstance().hasRole(User.Role.MANAGER, User.Role.OWNER));
        assertTrue(SessionManager.getInstance().hasRole(User.Role.CLERK, User.Role.MANAGER, User.Role.OWNER));
    }

    @Test @Order(43) @DisplayName("W5.4: Logout invalidates all access")
    void w5_logout() {
        SessionManager.getInstance().logout();
        assertFalse(SessionManager.getInstance().isAuthenticated());
        assertFalse(SessionManager.getInstance().hasRole(User.Role.OWNER));
    }
}
