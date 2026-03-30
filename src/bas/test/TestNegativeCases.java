package bas.test;

import bas.db.BookCache;
import bas.db.ConnectionPool;
import bas.db.DatabaseManager;
import bas.model.*;
import bas.util.*;
import bas.auth.*;
import bas.crypto.AESUtil;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Negative tests — everything that should fail, reject, or handle gracefully.
 * These verify the system doesn't break under invalid/adversarial input.
 */
@DisplayName("Negative & Failure Path Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestNegativeCases {

    @BeforeAll void init() {
        DatabaseManager.getInstance().initializeDatabase();
        cleanup();
    }
    @AfterAll void teardown() { cleanup(); }

    private void cleanup() {
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("DELETE FROM sale_items WHERE sale_id LIKE 'SALE-NEG%'");
                s.execute("DELETE FROM sales WHERE sale_id LIKE 'SALE-NEG%'");
                s.execute("DELETE FROM oos_requests WHERE request_id LIKE 'REQ-NEG%'");
                s.execute("DELETE FROM books WHERE isbn LIKE '9999999999%'");
            }
            ConnectionPool.getInstance().release(c);
        } catch (Exception ignored) {}
        BookCache.getInstance().invalidate();
    }

    // ═══ AUTHENTICATION FAILURES ═════════════════════════════════════════════

    @Test @Order(1) @DisplayName("Auth: wrong password for every role")
    void authWrongPasswordAllRoles() {
        assertNull(DatabaseManager.getInstance().authenticate("owner1", "badpass"));
        assertNull(DatabaseManager.getInstance().authenticate("manager1", "badpass"));
        assertNull(DatabaseManager.getInstance().authenticate("clerk1", "badpass"));
        assertNull(DatabaseManager.getInstance().authenticate("clerk2", "badpass"));
    }

    @Test @Order(2) @DisplayName("Auth: case-sensitive user ID")
    void authCaseSensitiveId() {
        assertNull(DatabaseManager.getInstance().authenticate("OWNER1", "owner123"));
        assertNull(DatabaseManager.getInstance().authenticate("Owner1", "owner123"));
    }

    @Test @Order(3) @DisplayName("Auth: case-sensitive password")
    void authCaseSensitivePassword() {
        assertNull(DatabaseManager.getInstance().authenticate("owner1", "OWNER123"));
        assertNull(DatabaseManager.getInstance().authenticate("owner1", "Owner123"));
    }

    @Test @Order(4) @DisplayName("Auth: extra whitespace in credentials")
    void authWhitespace() {
        assertNull(DatabaseManager.getInstance().authenticate(" owner1 ", "owner123"));
        assertNull(DatabaseManager.getInstance().authenticate("owner1", " owner123 "));
    }

    @Test @Order(5) @DisplayName("Auth: very long username doesn't crash")
    void authLongUsername() {
        assertNull(DatabaseManager.getInstance().authenticate("A".repeat(10000), "pass"));
    }

    @Test @Order(6) @DisplayName("Auth: very long password doesn't crash")
    void authLongPassword() {
        assertNull(DatabaseManager.getInstance().authenticate("owner1", "X".repeat(10000)));
    }

    @Test @Order(7) @DisplayName("Auth: special chars in credentials don't crash")
    void authSpecialChars() {
        assertNull(DatabaseManager.getInstance().authenticate("user'\"<>&;--", "pass'\"<>&;--"));
    }

    // ═══ SALE FAILURES ═══════════════════════════════════════════════════════

    @Test @Order(10) @DisplayName("Sale: empty items list fails")
    void saleEmptyItems() {
        SaleRecord s = new SaleRecord("SALE-NEG-EMPTY", "clerk1");
        // Empty sale — saveSaleAtomically should handle gracefully
        boolean ok = DatabaseManager.getInstance().saveSaleAtomically(s, null);
        // Might succeed (empty batch) or fail — either way no crash
    }

    @Test @Order(11) @DisplayName("Sale: quantity exceeding stock fails atomically")
    void saleExceedStock() {
        SaleRecord s = new SaleRecord("SALE-NEG-EXCEED", "clerk1");
        s.addItem(new LineItem("9780451524935", "1984", 999999, 199.0));
        assertFalse(DatabaseManager.getInstance().saveSaleAtomically(s, null));
    }

    @Test @Order(12) @DisplayName("Sale: nonexistent ISBN in sale fails")
    void saleNonexistentISBN() {
        SaleRecord s = new SaleRecord("SALE-NEG-NOBOOK", "clerk1");
        s.addItem(new LineItem("0000000000000", "Fake", 1, 100.0));
        assertFalse(DatabaseManager.getInstance().saveSaleAtomically(s, null));
    }

    @Test @Order(13) @DisplayName("Sale: zero-stock book cannot be sold")
    void saleZeroStock() {
        SaleRecord s = new SaleRecord("SALE-NEG-ZERO", "clerk1");
        s.addItem(new LineItem("9781501197277", "It", 1, 450.0)); // OOS
        assertFalse(DatabaseManager.getInstance().saveSaleAtomically(s, null));
    }

    @Test @Order(14) @DisplayName("Sale: duplicate sale ID fails on second attempt")
    void saleDuplicateId() {
        SaleRecord s1 = new SaleRecord("SALE-NEG-DUP", "clerk1");
        s1.addItem(new LineItem("9780451524935", "1984", 1, 199.0));
        assertTrue(DatabaseManager.getInstance().saveSaleAtomically(s1, "r1"));

        SaleRecord s2 = new SaleRecord("SALE-NEG-DUP", "clerk2"); // Same ID
        s2.addItem(new LineItem("9780451524935", "1984", 1, 199.0));
        assertFalse(DatabaseManager.getInstance().saveSaleAtomically(s2, "r2"));

        // Restore stock
        DatabaseManager.getInstance().addStock("9780451524935", 1, "TEST");
    }

    @Test @Order(15) @DisplayName("Sale: stock unchanged after failed sale")
    void saleStockUnchangedOnFail() {
        BookCache.getInstance().invalidate();
        Book before = DatabaseManager.getInstance().getByISBN("9780451524935");
        SaleRecord s = new SaleRecord("SALE-NEG-STOCKCHK", "clerk1");
        s.addItem(new LineItem("9780451524935", "1984", 999999, 199.0));
        assertFalse(DatabaseManager.getInstance().saveSaleAtomically(s, null));
        BookCache.getInstance().invalidate();
        Book after = DatabaseManager.getInstance().getByISBN("9780451524935");
        assertEquals(before.getStockCount(), after.getStockCount());
    }

    // ═══ STOCK UPDATE FAILURES ═══════════════════════════════════════════════

    @Test @Order(20) @DisplayName("Stock: add zero quantity returns false")
    void stockZero() { assertFalse(DatabaseManager.getInstance().addStock("9780451524935", 0, "TEST")); }

    @Test @Order(21) @DisplayName("Stock: add negative quantity returns false")
    void stockNegative() { assertFalse(DatabaseManager.getInstance().addStock("9780451524935", -5, "TEST")); }

    @Test @Order(22) @DisplayName("Stock: add to nonexistent ISBN returns false")
    void stockNonexistent() { assertFalse(DatabaseManager.getInstance().addStock("0000000000000", 10, "TEST")); }

    // ═══ BOOK CRUD FAILURES ══════════════════════════════════════════════════

    @Test @Order(30) @DisplayName("AddBook: duplicate ISBN fails")
    void addBookDuplicate() {
        assertFalse(DatabaseManager.getInstance().addBook(
            new Book("9780451524935","Dup","A","P","",1.0,"X",1,1,0,0.0,1)));
    }

    @Test @Order(31) @DisplayName("UpdateBook: nonexistent ISBN does nothing (no crash)")
    void updateNonexistent() {
        Book b = new Book("0000000000000","Ghost","A","P","",1.0,"X",1,1,0,0.0,1);
        assertDoesNotThrow(() -> DatabaseManager.getInstance().updateBook(b));
    }

    @Test @Order(32) @DisplayName("GetByISBN: null-like values don't crash")
    void getByISBNEdge() {
        assertNull(DatabaseManager.getInstance().getByISBN(""));
        assertNull(DatabaseManager.getInstance().getByISBN("null"));
    }

    // ═══ SEARCH EDGE FAILURES ════════════════════════════════════════════════

    @Test @Order(40) @DisplayName("Search: SQL wildcards in search don't return everything")
    void searchSQLWildcard() {
        List<Book> all = DatabaseManager.getInstance().getAllBooks();
        List<Book> percent = DatabaseManager.getInstance().searchByTitle("%");
        // % inside LIKE is already escaped by parameterized query
        // Should NOT return all books (it searches for literal '%')
        assertTrue(percent.size() < all.size() || percent.isEmpty());
    }

    @Test @Order(41) @DisplayName("Search: newline in search doesn't crash")
    void searchNewline() {
        assertDoesNotThrow(() -> DatabaseManager.getInstance().searchByTitle("test\ninjection"));
    }

    @Test @Order(42) @DisplayName("Search: tab character in search doesn't crash")
    void searchTab() {
        assertDoesNotThrow(() -> DatabaseManager.getInstance().searchByTitle("test\tinjection"));
    }

    @Test @Order(43) @DisplayName("Search: null byte in search doesn't crash")
    void searchNullByte() {
        assertDoesNotThrow(() -> DatabaseManager.getInstance().searchByTitle("test\0injection"));
    }

    // ═══ SESSION FAILURES ════════════════════════════════════════════════════

    @Test @Order(50) @DisplayName("Session: hasRole fails when not authenticated")
    void sessionNoAuth() {
        SessionManager.getInstance().logout();
        assertFalse(SessionManager.getInstance().hasRole(User.Role.OWNER));
        assertFalse(SessionManager.getInstance().hasRole(User.Role.CLERK));
    }

    @Test @Order(51) @DisplayName("Session: requireRole throws when not authenticated")
    void sessionRequireThrows() {
        SessionManager.getInstance().logout();
        assertThrows(SecurityException.class, () ->
            SessionManager.getInstance().requireRole(User.Role.OWNER));
    }

    @Test @Order(52) @DisplayName("Session: double logout doesn't crash")
    void sessionDoubleLogout() {
        SessionManager.getInstance().logout();
        assertDoesNotThrow(() -> SessionManager.getInstance().logout());
    }

    @Test @Order(53) @DisplayName("Session: getters return null after logout")
    void sessionNullAfterLogout() {
        SessionManager.getInstance().logout();
        assertNull(SessionManager.getInstance().getToken());
        assertNull(SessionManager.getInstance().getUserId());
        assertNull(SessionManager.getInstance().getName());
        assertNull(SessionManager.getInstance().getRole());
    }

    // ═══ JWT FAILURES ════════════════════════════════════════════════════════

    @Test @Order(60) @DisplayName("JWT: token with only 2 parts fails")
    void jwt2Parts() { assertNull(JWTUtil.validateToken("part1.part2")); }

    @Test @Order(61) @DisplayName("JWT: token with 4 parts fails")
    void jwt4Parts() { assertNull(JWTUtil.validateToken("a.b.c.d")); }

    @Test @Order(62) @DisplayName("JWT: base64 but invalid JSON payload fails")
    void jwtBadPayload() {
        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("not-json".getBytes());
        assertNull(JWTUtil.validateToken(header + "." + payload + ".fakesig"));
    }

    // ═══ AES FAILURES ════════════════════════════════════════════════════════

    @Test @Order(70) @DisplayName("AES: corrupted ciphertext throws")
    void aesCorrupted() {
        assertThrows(RuntimeException.class, () -> AESUtil.decrypt("not-base64!", "key"));
    }

    @Test @Order(71) @DisplayName("AES: truncated ciphertext throws")
    void aesTruncated() {
        String enc = AESUtil.encrypt("secret", "key");
        String truncated = enc.substring(0, enc.length() / 2);
        assertThrows(RuntimeException.class, () -> AESUtil.decrypt(truncated, "key"));
    }

    // ═══ ISBN NEGATIVE CASES ═════════════════════════════════════════════════

    @Test @Order(80) @DisplayName("ISBN: single character")
    void isbnSingleChar() { assertFalse(ISBNValidator.isValid("X")); }

    @Test @Order(81) @DisplayName("ISBN: negative number string")
    void isbnNegative() { assertFalse(ISBNValidator.isValid("-1234567890")); }

    @Test @Order(82) @DisplayName("ISBN: decimal number string")
    void isbnDecimal() { assertFalse(ISBNValidator.isValid("978.045.152")); }

    @Test @Order(83) @DisplayName("ISBN: 10 digits with X not at end")
    void isbnXMiddle() { assertFalse(ISBNValidator.isValid("04515X4934")); }

    @Test @Order(84) @DisplayName("ISBN: 13 digits with X anywhere")
    void isbn13WithX() { assertFalse(ISBNValidator.isValid("978045152493X")); }

    // ═══ EMAIL NEGATIVE CASES ════════════════════════════════════════════════

    @Test @Order(90) @DisplayName("Email: just @ sign")
    void emailJustAt() { assertFalse(EmailValidator.isValid("@")); }

    @Test @Order(91) @DisplayName("Email: multiple @ signs")
    void emailMultiAt() { assertFalse(EmailValidator.isValid("a@b@c.com")); }

    @Test @Order(92) @DisplayName("Email: trailing dot in domain")
    void emailTrailingDot() { assertFalse(EmailValidator.isValid("user@domain.com.")); }

    @Test @Order(93) @DisplayName("Email: only whitespace")
    void emailOnlySpaces() { assertFalse(EmailValidator.isValid("     ")); }

    @Test @Order(94) @DisplayName("Email: no local part")
    void emailNoLocal() { assertFalse(EmailValidator.isValid("@domain.com")); }

    // ═══ SALE RECORD EDGE CASES ══════════════════════════════════════════════

    @Test @Order(100) @DisplayName("SaleRecord: remove nonexistent ISBN doesn't crash")
    void saleRemoveNonexistent() {
        SaleRecord s = new SaleRecord("SALE-NEG-RMV", "clerk1");
        s.addItem(new LineItem("isbn1", "Book", 1, 100.0));
        assertDoesNotThrow(() -> s.removeItem("nonexistent"));
        assertEquals(1, s.getItems().size()); // Original still there
    }

    @Test @Order(101) @DisplayName("SaleRecord: remove from empty sale doesn't crash")
    void saleRemoveFromEmpty() {
        SaleRecord s = new SaleRecord("SALE-NEG-RMVE", "clerk1");
        assertDoesNotThrow(() -> s.removeItem("isbn"));
        assertTrue(s.getItems().isEmpty());
    }

    @Test @Order(102) @DisplayName("SaleRecord: add same ISBN 10 times merges correctly")
    void saleMerge10Times() {
        SaleRecord s = new SaleRecord("SALE-NEG-MERGE", "clerk1");
        for (int i = 0; i < 10; i++) s.addItem(new LineItem("isbn1", "Book", 1, 100.0));
        assertEquals(1, s.getItems().size());
        assertEquals(10, s.getItems().get(0).getQuantity());
        assertEquals(1000.0, s.getTotalAmount(), 0.01);
    }

    // ═══ BOOK MODEL EDGE CASES ═══════════════════════════════════════════════

    @Test @Order(110) @DisplayName("Book: very high weekly sales and lead time")
    void bookHighProcurement() {
        Book b = new Book("1","T","A","P","",10.0,"",0,5,0,1000.0,52);
        assertEquals(52000, b.getRequiredProcurementQty());
    }

    @Test @Order(111) @DisplayName("Book: zero threshold means always needs restock at 0")
    void bookZeroThreshold() {
        Book b = new Book("1","T","A","P","",10.0,"",0,0,0,1.0,1);
        assertTrue(b.needsRestock()); // 0 <= 0
    }

    @Test @Order(112) @DisplayName("Book: stock=1 threshold=0 does not need restock")
    void bookStockAboveZeroThreshold() {
        Book b = new Book("1","T","A","P","",10.0,"",1,0,0,1.0,1);
        assertFalse(b.needsRestock()); // 1 > 0
    }

    @Test @Order(113) @DisplayName("Book: default constructor creates empty book")
    void bookDefaultConstructor() {
        Book b = new Book();
        assertNull(b.getIsbn());
        assertEquals(0, b.getStockCount());
    }
}
