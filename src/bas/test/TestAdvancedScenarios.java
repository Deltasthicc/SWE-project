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

    @BeforeAll void setup() { DatabaseManager.getInstance().initializeDatabase(); cleanup(); ensureStock(); }
    @AfterAll void teardown() { cleanup(); ensureStock(); }

    private void ensureStock() {
        try { java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("UPDATE books SET stock_count = GREATEST(stock_count, 20) WHERE isbn='9780451524935'");
                s.execute("UPDATE books SET stock_count = GREATEST(stock_count, 20) WHERE isbn='9780062315007'");
                s.execute("UPDATE books SET stock_count = GREATEST(stock_count, 20) WHERE isbn='9781982173593'");
                s.execute("UPDATE books SET stock_count = GREATEST(stock_count, 3)  WHERE isbn='9780062409850'");
            } ConnectionPool.getInstance().release(c); BookCache.getInstance().invalidate();
        } catch (Exception e) { System.err.println("[TestSetup] "+e.getMessage()); }
    }
    private void cleanup() {
        try { java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("DELETE FROM sale_items WHERE sale_id LIKE 'SALE-ADV%'");
                s.execute("DELETE FROM sales WHERE sale_id LIKE 'SALE-ADV%'");
                s.execute("DELETE FROM oos_requests WHERE request_id LIKE 'REQ-ADV%'");
                s.execute("DELETE FROM books WHERE isbn='9999999999998'");
            } ConnectionPool.getInstance().release(c);
        } catch (Exception ignored) {} BookCache.getInstance().invalidate();
    }

    // Config
    @Test @Order(1) @DisplayName("Config: DB_HOST is non-empty") void configDbHost() { assertFalse(AppConfig.DB_HOST.isBlank()); }
    @Test @Order(2) @DisplayName("Config: DB_PORT is valid") void configDbPort() { assertTrue(AppConfig.DB_PORT>0&&AppConfig.DB_PORT<=65535); }
    @Test @Order(3) @DisplayName("Config: DB_USER is non-empty") void configDbUser() { assertFalse(AppConfig.DB_USER.isBlank()); }
    @Test @Order(4) @DisplayName("Config: DB_PASSWORD is non-empty") void configDbPassword() { assertFalse(AppConfig.DB_PASSWORD.isBlank()); }
    @Test @Order(5) @DisplayName("Config: JWT_SECRET >= 32 chars") void configJwtSecret() { assertTrue(AppConfig.JWT_SECRET.length()>=32); }
    @Test @Order(6) @DisplayName("Config: JWT_EXPIRY_HOURS > 0") void configJwtExpiry() { assertTrue(AppConfig.JWT_EXPIRY_HOURS>0); }
    @Test @Order(7) @DisplayName("Config: SMTP_HOST non-empty") void configSmtpHost() { assertFalse(AppConfig.SMTP_HOST.isBlank()); }
    @Test @Order(8) @DisplayName("Config: SMTP_PORT is 587") void configSmtpPort() { assertEquals(587,AppConfig.SMTP_PORT); }
    @Test @Order(9) @DisplayName("Config: SMTP_EMAIL contains @") void configSmtpEmail() { assertTrue(AppConfig.SMTP_EMAIL.contains("@")); }
    @Test @Order(10) @DisplayName("Config: AES_KEY non-empty") void configAesKey() { assertFalse(AppConfig.AES_KEY.isBlank()); }

    // Multi-item sale
    @Test @Order(20) @DisplayName("MultiSale: 3 books in one transaction")
    void multiItemSale() {
        SaleRecord sale = new SaleRecord("SALE-ADV-MULTI","clerk1");
        sale.addItem(new LineItem("9780451524935","1984",1,199.0));
        sale.addItem(new LineItem("9780062315007","The Alchemist",2,310.0));
        sale.addItem(new LineItem("9781982173593","Atomic Habits",1,499.0));
        assertEquals(3,sale.getItems().size());
        String receipt = PrinterUtil.buildReceiptString(sale);
        assertTrue(DatabaseManager.getInstance().saveSaleAtomically(sale,receipt),"Multi-item sale should succeed");
        assertEquals(3, DatabaseManager.getInstance().getSaleItems("SALE-ADV-MULTI").size());
    }
    @Test @Order(21) @DisplayName("MultiSale: receipt stored correctly")
    void multiItemReceipt() { assertNotNull(DatabaseManager.getInstance().getReceiptContent("SALE-ADV-MULTI")); }

    // Boundary: buy last copy
    @Test @Order(30) @DisplayName("Boundary: buy exactly the last copies")
    void buyLastCopy() {
        BookCache.getInstance().invalidate(); Book b = DatabaseManager.getInstance().getByISBN("9780062409850"); if(b==null||b.getStockCount()<1) return;
        int stock = b.getStockCount();
        SaleRecord sale = new SaleRecord("SALE-ADV-LAST","clerk1"); sale.addItem(new LineItem(b.getIsbn(),b.getTitle(),stock,b.getUnitPrice()));
        assertTrue(DatabaseManager.getInstance().saveSaleAtomically(sale,"last-copy"));
        BookCache.getInstance().invalidate(); assertEquals(0, DatabaseManager.getInstance().getByISBN(b.getIsbn()).getStockCount());
        DatabaseManager.getInstance().addStock(b.getIsbn(), stock, "TEST");
    }
    @Test @Order(31) @DisplayName("Boundary: zero-stock book fails sale") void buyFromZeroStock() { SaleRecord s=new SaleRecord("SALE-ADV-ZERO","clerk1"); s.addItem(new LineItem("9781501197277","It",1,450.0)); assertFalse(DatabaseManager.getInstance().saveSaleAtomically(s,null)); }

    // Date ranges
    @Test @Order(40) @DisplayName("DateRange: same day") void sameDayRange() { String t=java.time.LocalDate.now().toString(); assertNotNull(DatabaseManager.getInstance().getSalesStats(t,t)); }
    @Test @Order(41) @DisplayName("DateRange: reversed = zero") void reversedDates() { assertEquals(0.0,DatabaseManager.getInstance().getTotalRevenue("2025-12-31","2025-01-01")); }
    @Test @Order(42) @DisplayName("DateRange: ancient = zero") void ancientDates() { assertEquals(0.0,DatabaseManager.getInstance().getTotalRevenue("1900-01-01","1900-12-31")); }
    @Test @Order(43) @DisplayName("DateRange: full year captures sales") void fullYearRange() { assertTrue(DatabaseManager.getInstance().getTotalRevenue(java.time.LocalDate.now().minusDays(365).toString(),java.time.LocalDate.now().plusDays(1).toString())>0); }

    // OOS edge cases
    @Test @Order(50) @DisplayName("OOS: unknown ISBN succeeds") void oosUnknownISBN() { assertTrue(DatabaseManager.getInstance().addOOSRequest(new OOSRequest("REQ-ADV-UNK","UNKNOWN","Requested Book","Unknown","Unknown","x@y.com"))); }
    @Test @Order(51) @DisplayName("OOS: very long title succeeds") void oosLongTitle() { assertTrue(DatabaseManager.getInstance().addOOSRequest(new OOSRequest("REQ-ADV-LONG","UNKNOWN","A".repeat(500),"Author","Pub",null))); }
    @Test @Order(52) @DisplayName("OOS: pending for non-existent ISBN = empty") void oosPendingNonExistent() { assertTrue(DatabaseManager.getInstance().getPendingEmails("0000000000000").isEmpty()); }
    @Test @Order(53) @DisplayName("OOS: markNotified on non-existent = no crash") void oosMarkNonExistent() { assertDoesNotThrow(()->DatabaseManager.getInstance().markNotified("0000000000000")); }

    // Cache consistency
    @Test @Order(60) @DisplayName("Cache: addBook invalidates + appears") void cacheAfterAdd() {
        DatabaseManager.getInstance().addBook(new Book("9999999999998","Cache Test","A","P","Addr",100.0,"Z-99",5,3,0,1.0,1));
        assertNotNull(DatabaseManager.getInstance().getByISBN("9999999999998"));
    }
    @Test @Order(61) @DisplayName("Cache: updateBook reflects") void cacheAfterUpdate() {
        BookCache.getInstance().invalidate(); Book b=DatabaseManager.getInstance().getByISBN("9780451524935"); double orig=b.getUnitPrice();
        b.setUnitPrice(999.0); DatabaseManager.getInstance().updateBook(b);
        assertEquals(999.0, DatabaseManager.getInstance().getByISBN("9780451524935").getUnitPrice());
        b.setUnitPrice(orig); DatabaseManager.getInstance().updateBook(b);
    }

    // Search quality
    @Test @Order(70) @DisplayName("Search: sorted alphabetically") void searchSorted() { List<Book> b=DatabaseManager.getInstance().getAllBooks(); for(int i=1;i<b.size();i++) assertTrue(b.get(i).getTitle().compareToIgnoreCase(b.get(i-1).getTitle())>=0); }
    @Test @Order(71) @DisplayName("Search: partial ISBN = null") void searchPartialISBN() { assertNull(DatabaseManager.getInstance().getByISBN("978045")); }
    @Test @Order(72) @DisplayName("Search: same author finds multiple") void searchSameAuthor() { assertTrue(DatabaseManager.getInstance().searchByAuthor("J.K. Rowling").size()>=3); }

    // Logs
    @Test @Order(80) @DisplayName("Logs: INIT exists") void logsHaveInit() { assertTrue(DatabaseManager.getInstance().getRecentLogs(10000).stream().anyMatch(l->"INIT".equals(l[2]))); }
    @Test @Order(81) @DisplayName("Logs: SALE exists") void logsHaveSales() { assertTrue(DatabaseManager.getInstance().getRecentLogs(10000).stream().anyMatch(l->"SALE".equals(l[2]))); }
    @Test @Order(82) @DisplayName("Logs: entries have timestamps") void logsHaveTimestamps() { for(Object[] l:DatabaseManager.getInstance().getRecentLogs(10)){assertNotNull(l[1]);assertTrue(l[1].toString().length()>=10);} }

    // Receipt format
    @Test @Order(90) @DisplayName("Receipt: separators") void receiptSeparators() { SaleRecord s=new SaleRecord("S","c"); s.addItem(new LineItem("i","B",1,100.0)); String r=PrinterUtil.buildReceiptString(s); assertTrue(r.contains("====")); assertTrue(r.contains("----")); }
    @Test @Order(91) @DisplayName("Receipt: Thank you") void receiptThankYou() { SaleRecord s=new SaleRecord("S","c"); s.addItem(new LineItem("i","B",1,100.0)); assertTrue(PrinterUtil.buildReceiptString(s).contains("Thank you")); }
    @Test @Order(92) @DisplayName("Receipt: clerk ID") void receiptClerk() { SaleRecord s=new SaleRecord("S","clerk2"); s.addItem(new LineItem("i","B",1,100.0)); assertTrue(PrinterUtil.buildReceiptString(s).contains("clerk2")); }
    @Test @Order(93) @DisplayName("Receipt: total accuracy") void receiptTotalAccuracy() { SaleRecord s=new SaleRecord("S","c"); s.addItem(new LineItem("i1","A",2,150.0)); s.addItem(new LineItem("i2","B",3,200.0)); assertTrue(PrinterUtil.buildReceiptString(s).contains("900.00")); }

    // ID generation
    @Test @Order(95) @DisplayName("ID: newSaleId format") void saleIdFormat() { String id=DatabaseManager.newSaleId(); assertTrue(id.startsWith("SALE-")); assertEquals(13,id.length()); }
    @Test @Order(96) @DisplayName("ID: newReqId format") void reqIdFormat() { String id=DatabaseManager.newReqId(); assertTrue(id.startsWith("REQ-")); assertEquals(12,id.length()); }
    @Test @Order(97) @DisplayName("ID: 100 unique") void idsUnique() { java.util.Set<String> ids=new java.util.HashSet<>(); for(int i=0;i<100;i++) ids.add(DatabaseManager.newSaleId()); assertEquals(100,ids.size()); }
}
