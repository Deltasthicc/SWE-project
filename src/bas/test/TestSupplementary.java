package bas.test;

import bas.auth.JWTUtil;
import bas.auth.SessionManager;
import bas.crypto.AESUtil;
import bas.db.BookCache;
import bas.db.DatabaseManager;
import bas.model.*;
import bas.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * 20 supplementary tests — rounding out the full 420 test suite.
 * Covers remaining untested edges and behaviors.
 */
@DisplayName("Supplementary Tests (Round to 420)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestSupplementary {

    @BeforeAll static void init() { DatabaseManager.getInstance().initializeDatabase(); }

    @Test @Order(1) @DisplayName("Book: publisherAddress can be null without error")
    void bookNullPublisherAddress() {
        Book b = new Book("isbn","Title","Author","Publisher",null,100.0,"A-1",10,5,0,1.0,1);
        assertNull(b.getPublisherAddress());
    }

    @Test @Order(2) @DisplayName("OOSRequest: getFormattedTimestamp matches yyyy-MM-dd HH:mm:ss")
    void oosTimestampFormat() {
        OOSRequest req = new OOSRequest("REQ-FMT","isbn","T","A","P","e@m.com");
        assertTrue(req.getFormattedTimestamp().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test @Order(3) @DisplayName("SaleRecord: clerkId stored correctly")
    void saleClerkId() {
        SaleRecord s = new SaleRecord("SALE-CLK", "clerk2");
        assertEquals("clerk2", s.getClerkId());
    }

    @Test @Order(4) @DisplayName("SaleRecord: saleId stored correctly")
    void saleSaleId() {
        SaleRecord s = new SaleRecord("SALE-MY-ID", "clerk1");
        assertEquals("SALE-MY-ID", s.getSaleId());
    }

    @Test @Order(5) @DisplayName("LineItem: getIsbn returns correct value")
    void lineItemIsbn() {
        LineItem li = new LineItem("9780451524935", "1984", 1, 199.0);
        assertEquals("9780451524935", li.getIsbn());
    }

    @Test @Order(6) @DisplayName("LineItem: getTitle returns correct value")
    void lineItemTitle() {
        LineItem li = new LineItem("isbn", "My Book Title", 1, 100.0);
        assertEquals("My Book Title", li.getTitle());
    }

    @Test @Order(7) @DisplayName("Book: procurement qty with very large stock returns 0")
    void bookProcurementLargeStock() {
        Book b = new Book("1","T","A","P","",10.0,"",100000,5,0,10.0,2);
        assertEquals(0, b.getRequiredProcurementQty());
    }

    @Test @Order(8) @DisplayName("ISBN: clean strips only hyphens from pure-hyphen input")
    void isbnCleanOnlyHyphens() {
        assertEquals("", ISBNValidator.clean("---"));
    }

    @Test @Order(9) @DisplayName("Email: numeric domain is valid")
    void emailNumericDomain() {
        assertTrue(EmailValidator.isValid("user@123domain.com"));
    }

    @Test @Order(10) @DisplayName("Email: hyphen at start of domain fails")
    void emailHyphenStartDomain() {
        assertFalse(EmailValidator.isValid("user@-domain.com"));
    }

    @Test @Order(11) @DisplayName("JWT: extractLong returns valid iat from token")
    void jwtExtractIat() {
        String token = JWTUtil.generateToken("u1", "Name", "OWNER");
        String payload = JWTUtil.validateToken(token);
        long iat = JWTUtil.extractLong(payload, "iat");
        assertTrue(iat > 0, "iat should be a positive unix timestamp");
        assertTrue(iat <= System.currentTimeMillis() / 1000 + 5, "iat should be near current time");
    }

    @Test @Order(12) @DisplayName("JWT: exp is in the future")
    void jwtExpFuture() {
        String token = JWTUtil.generateToken("u1", "Name", "CLERK");
        String payload = JWTUtil.validateToken(token);
        long exp = JWTUtil.extractLong(payload, "exp");
        assertTrue(exp > System.currentTimeMillis() / 1000, "exp should be in the future");
    }

    @Test @Order(13) @DisplayName("AES: encrypt with very long key works")
    void aesLongKey() {
        String key = "K".repeat(1000);
        String enc = AESUtil.encrypt("data", key);
        assertEquals("data", AESUtil.decrypt(enc, key));
    }

    @Test @Order(14) @DisplayName("SaleRecord: total precision with fractional prices")
    void salePrecision() {
        SaleRecord s = new SaleRecord("SALE-PREC", "clerk1");
        s.addItem(new LineItem("i1", "B1", 3, 99.99));
        assertEquals(299.97, s.getTotalAmount(), 0.001);
    }

    @Test @Order(15) @DisplayName("Cache: searchByTitle for empty string returns all books")
    void cacheSearchEmpty() {
        List<Book> all = BookCache.getInstance().getAllBooks();
        List<Book> empty = BookCache.getInstance().searchByTitle("");
        assertEquals(all.size(), empty.size(), "Empty search should match all books");
    }

    @Test @Order(16) @DisplayName("Cache: searchByAuthor for empty string returns all books")
    void cacheAuthorSearchEmpty() {
        List<Book> all = BookCache.getInstance().getAllBooks();
        List<Book> empty = BookCache.getInstance().searchByAuthor("");
        assertEquals(all.size(), empty.size());
    }

    @Test @Order(17) @DisplayName("Session: getUserId matches the logged-in user")
    void sessionUserId() {
        User u = new User("testUser42", "Test Name", "hash", User.Role.MANAGER);
        SessionManager.getInstance().login(u);
        assertEquals("testUser42", SessionManager.getInstance().getUserId());
        assertEquals("Test Name", SessionManager.getInstance().getName());
        SessionManager.getInstance().logout();
    }

    @Test @Order(18) @DisplayName("Receipt: contains date/time of sale")
    void receiptContainsDate() {
        SaleRecord s = new SaleRecord("SALE-DATE", "clerk1");
        s.addItem(new LineItem("isbn", "Book", 1, 100.0));
        String receipt = PrinterUtil.buildReceiptString(s);
        // Should contain a date like 2026-03-30
        assertTrue(receipt.matches("(?s).*\\d{4}-\\d{2}-\\d{2}.*"), "Receipt should contain date");
    }

    @Test @Order(19) @DisplayName("Seed: Harry Potter books are correctly priced at 499 INR")
    void hpPricing() {
        Book hp1 = DatabaseManager.getInstance().getByISBN("9780590353427");
        assertNotNull(hp1);
        assertEquals(499.0, hp1.getUnitPrice(), 0.01);
    }

    @Test @Order(20) @DisplayName("Seed: out-of-stock books have correct rack locations")
    void oosRackLocations() {
        Book it = DatabaseManager.getInstance().getByISBN("9781501197277");
        assertNotNull(it);
        assertEquals(0, it.getStockCount());
        assertNotNull(it.getRackLocation());
        assertFalse(it.getRackLocation().isBlank(), "Even OOS books should have a rack location");
    }
}
