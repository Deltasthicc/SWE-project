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
import java.time.LocalDate;
import java.util.List;

@DisplayName("Integration Workflow Tests (End-to-End Scenarios)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestIntegrationWorkflow {

    private static final String INT_SALE_ID = "SALE-INT-001";
    private static final String ATOMIC_ISBN = "9781982173593";
    private static final String CIRCE_ISBN  = "9780062797155";

    @BeforeAll void setup() {
        DatabaseManager.getInstance().initializeDatabase();
        cleanup(); ensureStock();
    }
    @AfterAll void teardown() { cleanup(); ensureStock(); SessionManager.getInstance().logout(); BookCache.getInstance().invalidate(); }

    private void ensureStock() {
        try { java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("UPDATE books SET stock_count = GREATEST(stock_count, 20) WHERE isbn='" + ATOMIC_ISBN + "'");
                s.execute("UPDATE books SET stock_count = 0 WHERE isbn='" + CIRCE_ISBN + "'");
            } ConnectionPool.getInstance().release(c); BookCache.getInstance().invalidate();
        } catch (Exception e) { System.err.println("[TestSetup] ensureStock: " + e.getMessage()); }
    }
    private void cleanup() {
        try { java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("DELETE FROM sale_items WHERE sale_id LIKE 'SALE-INT%'");
                s.execute("DELETE FROM sales WHERE sale_id LIKE 'SALE-INT%'");
                s.execute("DELETE FROM oos_requests WHERE request_id LIKE 'REQ-INT%'");
            } ConnectionPool.getInstance().release(c);
        } catch (Exception ignored) {} BookCache.getInstance().invalidate();
    }

    // W1: POS Transaction
    @Test @Order(1) @DisplayName("W1.1: Clerk authenticates successfully")
    void w1_login() { User clerk = DatabaseManager.getInstance().authenticate("clerk1","clerk123"); assertNotNull(clerk); SessionManager.getInstance().login(clerk); assertTrue(SessionManager.getInstance().isAuthenticated()); }

    @Test @Order(2) @DisplayName("W1.2: Clerk searches for a book by title")
    void w1_search() { List<Book> r = DatabaseManager.getInstance().searchByTitle("Atomic Habits"); assertFalse(r.isEmpty()); assertTrue(r.get(0).isInStock(),"Atomic Habits should be in stock"); }

    @Test @Order(3) @DisplayName("W1.3: ISBN validation passes")
    void w1_validateISBN() { assertTrue(ISBNValidator.isValid(ATOMIC_ISBN)); }

    @Test @Order(4) @DisplayName("W1.4: Session validated (JWT)")
    void w1_sessionCheck() { assertTrue(SessionManager.getInstance().isAuthenticated()); }

    @Test @Order(5) @DisplayName("W1.5: Build sale and verify total")
    void w1_buildSale() { Book b = DatabaseManager.getInstance().getByISBN(ATOMIC_ISBN); assertNotNull(b); SaleRecord s = new SaleRecord(INT_SALE_ID,"clerk1"); s.addItem(new LineItem(b.getIsbn(),b.getTitle(),2,b.getUnitPrice())); assertEquals(2*b.getUnitPrice(),s.getTotalAmount(),0.001); }

    @Test @Order(6) @DisplayName("W1.6: Atomic sale commits, stock decrements, receipt stored")
    void w1_confirmSale() {
        BookCache.getInstance().invalidate(); Book before = DatabaseManager.getInstance().getByISBN(ATOMIC_ISBN); int sb = before.getStockCount();
        SaleRecord sale = new SaleRecord(INT_SALE_ID,"clerk1"); sale.addItem(new LineItem(before.getIsbn(),before.getTitle(),1,before.getUnitPrice()));
        String receipt = PrinterUtil.buildReceiptString(sale);
        assertTrue(DatabaseManager.getInstance().saveSaleAtomically(sale, receipt));
        BookCache.getInstance().invalidate(); Book after = DatabaseManager.getInstance().getByISBN(ATOMIC_ISBN);
        assertEquals(sb-1, after.getStockCount()); assertNotNull(DatabaseManager.getInstance().getReceiptContent(INT_SALE_ID));
    }

    @Test @Order(7) @DisplayName("W1.7: Transaction appears in history")
    void w1_history() { List<Object[]> txns = DatabaseManager.getInstance().getTransactionHistory(100); assertTrue(txns.stream().anyMatch(t -> INT_SALE_ID.equals(t[0]))); }

    // W2: OOS Request Flow
    @Test @Order(10) @DisplayName("W2.1: Customer finds out-of-stock book")
    void w2_searchOOS() { List<Book> r = DatabaseManager.getInstance().searchByTitle("Circe"); assertFalse(r.isEmpty()); assertEquals(0, r.get(0).getStockCount()); }

    @Test @Order(11) @DisplayName("W2.2: Customer submits OOS request")
    void w2_submitRequest() { assertTrue(DatabaseManager.getInstance().addOOSRequest(new OOSRequest("REQ-INT-001",CIRCE_ISBN,"Circe","Madeline Miller","Little Brown & Co.","customer@test.com"))); }

    @Test @Order(12) @DisplayName("W2.3: Request appears in OOS list")
    void w2_requestInList() { assertTrue(DatabaseManager.getInstance().getAllOOSRequests().stream().anyMatch(r->"REQ-INT-001".equals(r.getRequestId()))); }

    @Test @Order(13) @DisplayName("W2.4: Email in pending list")
    void w2_emailPending() { assertTrue(DatabaseManager.getInstance().getPendingEmails(CIRCE_ISBN).contains("customer@test.com")); }

    // W3: Owner Reports
    @Test @Order(20) @DisplayName("W3.1: Owner authenticates")
    void w3_ownerLogin() { User o = DatabaseManager.getInstance().authenticate("owner1","owner123"); assertNotNull(o); SessionManager.getInstance().login(o); assertTrue(SessionManager.getInstance().hasRole(User.Role.OWNER)); }

    @Test @Order(21) @DisplayName("W3.2: Owner generates sales report")
    void w3_salesReport() { String f=LocalDate.now().minusDays(30).toString(), t=LocalDate.now().toString(); assertFalse(DatabaseManager.getInstance().getSalesStats(f,t).isEmpty()); assertTrue(DatabaseManager.getInstance().getTotalRevenue(f,t)>0); }

    @Test @Order(22) @DisplayName("W3.3: Procurement report shows books below threshold")
    void w3_procurement() { List<Book> r = DatabaseManager.getInstance().getBooksNeedingRestock(); assertTrue(r.stream().anyMatch(b->b.getStockCount()==0)); }

    @Test @Order(23) @DisplayName("W3.4: Activity logs include our test sale")
    void w3_logs() { List<Object[]> logs = DatabaseManager.getInstance().getRecentLogs(200); assertTrue(logs.stream().anyMatch(l->{String m=l[4]!=null?l[4].toString():""; return m.contains(INT_SALE_ID);})); }

    // W4: Stock Update + Notification
    @Test @Order(30) @DisplayName("W4.1: Manager logs in")
    void w4_managerLogin() { assertNotNull(DatabaseManager.getInstance().authenticate("manager1","mgr123")); }

    @Test @Order(31) @DisplayName("W4.2: Manager adds stock to OOS book")
    void w4_addStock() { assertTrue(DatabaseManager.getInstance().addStock(CIRCE_ISBN,10,"manager1")); BookCache.getInstance().invalidate(); assertEquals(10, DatabaseManager.getInstance().getByISBN(CIRCE_ISBN).getStockCount()); }

    @Test @Order(32) @DisplayName("W4.3: Pending emails exist for restocked book")
    void w4_pendingEmails() { assertFalse(DatabaseManager.getInstance().getPendingEmails(CIRCE_ISBN).isEmpty()); }

    @Test @Order(33) @DisplayName("W4.4: Selective mark notified by email")
    void w4_markNotified() { DatabaseManager.getInstance().markNotifiedByEmail(CIRCE_ISBN,"customer@test.com"); assertFalse(DatabaseManager.getInstance().getPendingEmails(CIRCE_ISBN).contains("customer@test.com")); }

    @Test @Order(34) @DisplayName("W4.5: Cleanup — restore Circe")
    void w4_cleanup() { try{java.sql.Connection c=ConnectionPool.getInstance().borrow(); c.createStatement().execute("UPDATE books SET stock_count=0 WHERE isbn='"+CIRCE_ISBN+"'"); ConnectionPool.getInstance().release(c); BookCache.getInstance().invalidate();}catch(Exception ignored){} }

    // W5: Role Access
    @Test @Order(40) @DisplayName("W5.1: Clerk restricted from OWNER features")
    void w5_clerkRestricted() { SessionManager.getInstance().login(new User("c","C","h",User.Role.CLERK)); assertFalse(SessionManager.getInstance().hasRole(User.Role.OWNER)); }

    @Test @Order(41) @DisplayName("W5.2: Manager restricted from OWNER")
    void w5_managerAccess() { SessionManager.getInstance().login(new User("m","M","h",User.Role.MANAGER)); assertFalse(SessionManager.getInstance().hasRole(User.Role.OWNER)); }

    @Test @Order(42) @DisplayName("W5.3: Owner has full access")
    void w5_ownerFullAccess() { SessionManager.getInstance().login(new User("o","O","h",User.Role.OWNER)); assertTrue(SessionManager.getInstance().hasRole(User.Role.OWNER)); }

    @Test @Order(43) @DisplayName("W5.4: Logout invalidates access")
    void w5_logout() { SessionManager.getInstance().logout(); assertFalse(SessionManager.getInstance().isAuthenticated()); }
}
