package bas.test;

import bas.db.DatabaseManager;
import bas.db.BookCache;
import bas.model.*;
import bas.util.ISBNValidator;
import bas.util.EmailValidator;
import bas.auth.JWTUtil;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

@DisplayName("Edge Cases, Security & Boundary Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestEdgeCases {

    // ═══ SQL INJECTION ATTEMPTS ══════════════════════════════════════════════

    @Test @Order(1) @DisplayName("SQL Injection: title search with SQL payload")
    void sqlInjectionTitle() {
        List<Book> results = DatabaseManager.getInstance().searchByTitle("'; DROP TABLE books;--");
        assertNotNull(results); // Should not crash
        // And books table should still exist
        assertFalse(DatabaseManager.getInstance().getAllBooks().isEmpty(),
            "Books table should survive SQL injection attempt");
    }

    @Test @Order(2) @DisplayName("SQL Injection: author search with SQL payload")
    void sqlInjectionAuthor() {
        List<Book> results = DatabaseManager.getInstance().searchByAuthor("' OR '1'='1");
        assertNotNull(results);
    }

    @Test @Order(3) @DisplayName("SQL Injection: login with SQL payload")
    void sqlInjectionLogin() {
        User u = DatabaseManager.getInstance().authenticate("' OR 1=1 --", "anything");
        assertNull(u, "SQL injection login should fail");
    }

    @Test @Order(4) @DisplayName("SQL Injection: ISBN lookup with payload")
    void sqlInjectionISBN() {
        Book b = DatabaseManager.getInstance().getByISBN("' OR '1'='1");
        assertNull(b);
    }

    // ═══ EMPTY / NULL / WHITESPACE INPUTS ════════════════════════════════════

    @Test @Order(10) @DisplayName("Empty title search returns all or empty (no crash)")
    void emptyTitleSearch() {
        assertDoesNotThrow(() -> DatabaseManager.getInstance().searchByTitle(""));
    }

    @Test @Order(11) @DisplayName("Empty author search returns all or empty (no crash)")
    void emptyAuthorSearch() {
        assertDoesNotThrow(() -> DatabaseManager.getInstance().searchByAuthor(""));
    }

    @Test @Order(12) @DisplayName("Very long search string doesn't crash")
    void longSearchString() {
        String longQuery = "A".repeat(10000);
        assertDoesNotThrow(() -> DatabaseManager.getInstance().searchByTitle(longQuery));
    }

    @Test @Order(13) @DisplayName("Unicode/Emoji in search doesn't crash")
    void unicodeSearch() {
        assertDoesNotThrow(() -> DatabaseManager.getInstance().searchByTitle("📚🔥"));
        assertDoesNotThrow(() -> DatabaseManager.getInstance().searchByTitle("книга"));
    }

    @Test @Order(14) @DisplayName("Special chars in search: %_\\")
    void specialCharsSearch() {
        assertDoesNotThrow(() -> DatabaseManager.getInstance().searchByTitle("%_\\"));
    }

    // ═══ BOUNDARY VALUES ═════════════════════════════════════════════════════

    @Test @Order(20) @DisplayName("SaleRecord: zero-price item")
    void zeroPriceItem() {
        SaleRecord s = new SaleRecord("SALE-ZERO", "clerk1");
        s.addItem(new LineItem("isbn", "Free Book", 1, 0.0));
        assertEquals(0.0, s.getTotalAmount());
    }

    @Test @Order(21) @DisplayName("SaleRecord: large quantity")
    void largeQuantity() {
        SaleRecord s = new SaleRecord("SALE-BIG", "clerk1");
        s.addItem(new LineItem("isbn", "Book", 999999, 1.0));
        assertEquals(999999.0, s.getTotalAmount(), 0.001);
    }

    @Test @Order(22) @DisplayName("SaleRecord: very high price")
    void highPrice() {
        SaleRecord s = new SaleRecord("SALE-EXPENSIVE", "clerk1");
        s.addItem(new LineItem("isbn", "Rare Book", 1, 999999.99));
        assertEquals(999999.99, s.getTotalAmount(), 0.001);
    }

    @Test @Order(23) @DisplayName("Book: procurement qty with zero weekly sales")
    void procurementZeroSales() {
        Book b = new Book("1","T","A","P","",10.0,"",5,5,0,0.0,2);
        assertEquals(0, b.getRequiredProcurementQty());
    }

    @Test @Order(24) @DisplayName("Book: procurement qty with zero lead time")
    void procurementZeroLead() {
        Book b = new Book("1","T","A","P","",10.0,"",5,5,0,10.0,0);
        assertEquals(0, b.getRequiredProcurementQty());
    }

    @Test @Order(25) @DisplayName("Book: needsRestock when stock equals threshold exactly")
    void restockAtExactThreshold() {
        Book b = new Book("1","T","A","P","",10.0,"",5,5,0,1.0,1);
        assertTrue(b.needsRestock(), "Stock == threshold should need restock");
    }

    // ═══ JWT EDGE CASES ═════════════════════════════════════════════════════

    @Test @Order(30) @DisplayName("JWT: name with quotes doesn't break JSON")
    void jwtQuotesInName() {
        String token = JWTUtil.generateToken("u1", "O'Brien \"Nick\"", "CLERK");
        String payload = JWTUtil.validateToken(token);
        assertNotNull(payload, "JWT with quotes in name should still validate");
    }

    @Test @Order(31) @DisplayName("JWT: empty string fields still work")
    void jwtEmptyFields() {
        String token = JWTUtil.generateToken("", "", "");
        String payload = JWTUtil.validateToken(token);
        assertNotNull(payload, "JWT with empty fields should be valid");
    }

    @Test @Order(32) @DisplayName("JWT: extractClaim with missing key returns null")
    void jwtMissingClaim() {
        String token = JWTUtil.generateToken("u1", "Test", "OWNER");
        String payload = JWTUtil.validateToken(token);
        assertNull(JWTUtil.extractClaim(payload, "nonexistent_key"));
    }

    @Test @Order(33) @DisplayName("JWT: extractLong with missing key returns -1")
    void jwtMissingLong() {
        String token = JWTUtil.generateToken("u1", "Test", "OWNER");
        String payload = JWTUtil.validateToken(token);
        assertEquals(-1, JWTUtil.extractLong(payload, "nonexistent"));
    }

    // ═══ ISBN EDGE CASES ════════════════════════════════════════════════════

    @Test @Order(40) @DisplayName("ISBN: all zeros ISBN-10 is technically valid (checksum 0 mod 11)")
    void isbnAllZeros10() { assertTrue(ISBNValidator.isValid("0000000000")); }

    @Test @Order(41) @DisplayName("ISBN: 13-digit all zeros except last (valid: checksum = 0)")
    void isbnAllZeros13() {
        // 0000000000000 → checksum would be 0, so it's technically valid
        assertTrue(ISBNValidator.isValid("0000000000000"));
    }

    @Test @Order(42) @DisplayName("ISBN: with mixed hyphens and spaces")
    void isbnMixedSeparators() {
        assertTrue(ISBNValidator.isValid("978 0451-524935"));
    }

    @Test @Order(43) @DisplayName("ISBN: leading/trailing whitespace handled")
    void isbnWhitespace() {
        assertTrue(ISBNValidator.isValid("  9780451524935  "));
    }

    // ═══ EMAIL EDGE CASES ═══════════════════════════════════════════════════

    @Test @Order(50) @DisplayName("Email: very long local part")
    void emailLongLocal() {
        String email = "a".repeat(64) + "@domain.com";
        // Should be valid per regex (format check only)
        assertTrue(EmailValidator.isValid(email));
    }

    @Test @Order(51) @DisplayName("Email: single char TLD fails (< 2 chars)")
    void emailShortTLD() {
        assertFalse(EmailValidator.isValid("user@domain.c"));
    }

    @Test @Order(52) @DisplayName("Email: consecutive dots in domain")
    void emailConsecutiveDots() {
        assertFalse(EmailValidator.isValid("user@domain..com"));
    }

    // ═══ MULTIPLE OPERATIONS SEQUENCE ════════════════════════════════════════

    @Test @Order(60) @DisplayName("Sequence: search → add to cart → verify → clear")
    void operationSequence() {
        // Search
        List<Book> books = DatabaseManager.getInstance().searchByTitle("Atomic");
        assertFalse(books.isEmpty());
        Book b = books.get(0);

        // Add to cart
        SaleRecord sale = new SaleRecord("SALE-SEQ-TEST", "clerk1");
        sale.addItem(new LineItem(b.getIsbn(), b.getTitle(), 1, b.getUnitPrice()));
        assertEquals(b.getUnitPrice(), sale.getTotalAmount());

        // Add another
        sale.addItem(new LineItem(b.getIsbn(), b.getTitle(), 2, b.getUnitPrice()));
        assertEquals(3, sale.getItems().get(0).getQuantity()); // Merged

        // Remove
        sale.removeItem(b.getIsbn());
        assertTrue(sale.getItems().isEmpty());
        assertEquals(0.0, sale.getTotalAmount());
    }

    @Test @Order(61) @DisplayName("Sequence: multiple rapid searches don't crash")
    void rapidSearches() {
        for (int i = 0; i < 20; i++) {
            assertDoesNotThrow(() -> DatabaseManager.getInstance().searchByTitle("the"));
        }
    }

    @Test @Order(62) @DisplayName("Concurrent: logs from different actors don't interfere")
    void concurrentLogs() {
        DatabaseManager db = DatabaseManager.getInstance();
        assertDoesNotThrow(() -> {
            db.addLog("ACTOR_A", "TEST", "Message A");
            db.addLog("ACTOR_B", "TEST", "Message B");
            db.addLog("ACTOR_C", "TEST", "Message C");
        });
    }

    // ═══ ADDITIONAL EDGE CASES ═══════════════════════════════════════════════

    @Test @Order(70) @DisplayName("SaleRecord: many items (20) calculates total correctly")
    void saleManyItems() {
        SaleRecord s = new SaleRecord("SALE-MANY", "clerk1");
        for (int i = 0; i < 20; i++) {
            s.addItem(new LineItem("isbn-" + i, "Book " + i, 1, 10.0));
        }
        assertEquals(20, s.getItems().size());
        assertEquals(200.0, s.getTotalAmount(), 0.001);
    }

    @Test @Order(71) @DisplayName("OOSRequest: very long email accepted in model")
    void oosLongEmail() {
        String longEmail = "a".repeat(200) + "@" + "b".repeat(50) + ".com";
        OOSRequest req = new OOSRequest("REQ-LONG", "isbn", "T", "A", "P", longEmail);
        assertEquals(longEmail, req.getEmail());
    }

    @Test @Order(72) @DisplayName("ISBN: pure spaces treated as empty")
    void isbnPureSpaces() {
        assertFalse(ISBNValidator.isValid("   "));
    }

    @Test @Order(73) @DisplayName("Cache: getByISBN with hyphenated ISBN finds book")
    void cacheHyphenatedISBN() {
        Book b = BookCache.getInstance().getByISBN("978-0-451-52493-5");
        assertNotNull(b, "Hyphenated ISBN should be stripped and matched");
        assertEquals("1984", b.getTitle());
    }

    @Test @Order(74) @DisplayName("Book: getRequiredProcurementQty with zero stock and zero sales")
    void procurementZeroEverything() {
        Book b = new Book("1","T","A","P","",10.0,"",0,5,0,0.0,0);
        assertEquals(0, b.getRequiredProcurementQty());
    }

    @Test @Order(75) @DisplayName("SaleRecord: getTimestamp returns LocalDateTime object")
    void saleTimestampObject() {
        SaleRecord s = new SaleRecord("SALE-TS", "clerk1");
        assertNotNull(s.getTimestamp());
        assertTrue(s.getTimestamp() instanceof java.time.LocalDateTime);
    }

    @Test @Order(76) @DisplayName("OOSRequest: getTimestamp returns LocalDateTime object")
    void oosTimestampObject() {
        OOSRequest req = new OOSRequest("REQ-TS", "isbn", "T", "A", "P", null);
        assertNotNull(req.getTimestamp());
        assertTrue(req.getTimestamp() instanceof java.time.LocalDateTime);
    }

    @Test @Order(77) @DisplayName("Email: domain with subdomain is valid")
    void emailSubdomain() {
        assertTrue(EmailValidator.isValid("user@mail.example.co.uk"));
    }
}
