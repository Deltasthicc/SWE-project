package bas.test;

import bas.db.BookCache;
import bas.db.DatabaseManager;
import bas.model.Book;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@DisplayName("Seed Data Integrity Tests (Verify Catalogue & Demo Data)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestSeedDataIntegrity {

    @BeforeAll
    static void init() { DatabaseManager.getInstance().initializeDatabase(); }

    // ═══ BOOK COUNT & CATEGORIES ═════════════════════════════════════════════

    @Test @Order(1) @DisplayName("Seed: at least 70 books in catalogue")
    void bookCount() {
        List<Book> books = DatabaseManager.getInstance().getAllBooks();
        assertTrue(books.size() >= 70, "Expected 70+ books, got " + books.size());
    }

    @Test @Order(2) @DisplayName("Seed: all ISBNs are unique")
    void uniqueISBNs() {
        List<Book> books = DatabaseManager.getInstance().getAllBooks();
        Set<String> isbns = new HashSet<>();
        for (Book b : books) assertTrue(isbns.add(b.getIsbn()), "Duplicate ISBN: " + b.getIsbn());
    }

    @Test @Order(3) @DisplayName("Seed: all books have non-empty title")
    void allTitlesNonEmpty() {
        for (Book b : DatabaseManager.getInstance().getAllBooks())
            assertFalse(b.getTitle().isBlank(), "Book " + b.getIsbn() + " has blank title");
    }

    @Test @Order(4) @DisplayName("Seed: all books have non-empty author")
    void allAuthorsNonEmpty() {
        for (Book b : DatabaseManager.getInstance().getAllBooks())
            assertFalse(b.getAuthor().isBlank(), "Book " + b.getIsbn() + " has blank author");
    }

    @Test @Order(5) @DisplayName("Seed: all books have non-empty publisher")
    void allPublishersNonEmpty() {
        for (Book b : DatabaseManager.getInstance().getAllBooks())
            assertFalse(b.getPublisher().isBlank(), "Book " + b.getIsbn() + " has blank publisher");
    }

    @Test @Order(6) @DisplayName("Seed: all prices are positive")
    void allPricesPositive() {
        for (Book b : DatabaseManager.getInstance().getAllBooks())
            assertTrue(b.getUnitPrice() > 0, b.getTitle() + " has non-positive price: " + b.getUnitPrice());
    }

    @Test @Order(7) @DisplayName("Seed: no book has negative stock")
    void noNegativeStock() {
        for (Book b : DatabaseManager.getInstance().getAllBooks())
            assertTrue(b.getStockCount() >= 0, b.getTitle() + " has negative stock: " + b.getStockCount());
    }

    @Test @Order(8) @DisplayName("Seed: all rack locations are non-null")
    void allRackLocations() {
        for (Book b : DatabaseManager.getInstance().getAllBooks())
            assertNotNull(b.getRackLocation(), b.getTitle() + " has null rack location");
    }

    // ═══ GENRE DIVERSITY ═════════════════════════════════════════════════════

    @Test @Order(10) @DisplayName("Genre: has Literary Classics (rack A-*)")
    void hasClassics() {
        assertTrue(DatabaseManager.getInstance().getAllBooks().stream()
            .anyMatch(b -> b.getRackLocation().startsWith("A-")));
    }

    @Test @Order(11) @DisplayName("Genre: has Modern Fiction (rack B-*)")
    void hasModernFiction() {
        assertTrue(DatabaseManager.getInstance().getAllBooks().stream()
            .anyMatch(b -> b.getRackLocation().startsWith("B-")));
    }

    @Test @Order(12) @DisplayName("Genre: has Sci-Fi & Fantasy (rack C-*)")
    void hasSciFi() {
        assertTrue(DatabaseManager.getInstance().getAllBooks().stream()
            .anyMatch(b -> b.getRackLocation().startsWith("C-")));
    }

    @Test @Order(13) @DisplayName("Genre: has Mystery & Thriller (rack D-*)")
    void hasMystery() {
        assertTrue(DatabaseManager.getInstance().getAllBooks().stream()
            .anyMatch(b -> b.getRackLocation().startsWith("D-")));
    }

    @Test @Order(14) @DisplayName("Genre: has Non-Fiction (rack E-*)")
    void hasNonFiction() {
        assertTrue(DatabaseManager.getInstance().getAllBooks().stream()
            .anyMatch(b -> b.getRackLocation().startsWith("E-")));
    }

    @Test @Order(15) @DisplayName("Genre: has Indian Authors (rack F-*)")
    void hasIndianAuthors() {
        assertTrue(DatabaseManager.getInstance().getAllBooks().stream()
            .anyMatch(b -> b.getRackLocation().startsWith("F-")));
    }

    @Test @Order(16) @DisplayName("Genre: has Science/Philosophy (rack G-*)")
    void hasScience() {
        assertTrue(DatabaseManager.getInstance().getAllBooks().stream()
            .anyMatch(b -> b.getRackLocation().startsWith("G-")));
    }

    @Test @Order(17) @DisplayName("Genre: has Children's & YA (rack H-*)")
    void hasChildrens() {
        assertTrue(DatabaseManager.getInstance().getAllBooks().stream()
            .anyMatch(b -> b.getRackLocation().startsWith("H-")));
    }

    // ═══ SPECIFIC KEY BOOKS ══════════════════════════════════════════════════

    @Test @Order(20) @DisplayName("Book exists: 1984 by George Orwell")
    void has1984() { assertNotNull(DatabaseManager.getInstance().getByISBN("9780451524935")); }

    @Test @Order(21) @DisplayName("Book exists: Atomic Habits by James Clear")
    void hasAtomicHabits() { assertNotNull(DatabaseManager.getInstance().getByISBN("9781982173593")); }

    @Test @Order(22) @DisplayName("Book exists: Harry Potter #1")
    void hasHP1() { assertNotNull(DatabaseManager.getInstance().getByISBN("9780590353427")); }

    @Test @Order(23) @DisplayName("Book exists: Sapiens by Harari")
    void hasSapiens() { assertNotNull(DatabaseManager.getInstance().getByISBN("9780141988511")); }

    @Test @Order(24) @DisplayName("Book exists: Five Point Someone by Chetan Bhagat")
    void hasFivePoint() { assertNotNull(DatabaseManager.getInstance().getByISBN("9789350291863")); }

    @Test @Order(25) @DisplayName("Book exists: The Alchemist by Paulo Coelho")
    void hasAlchemist() { assertNotNull(DatabaseManager.getInstance().getByISBN("9780062315007")); }

    @Test @Order(26) @DisplayName("Book exists: Dune by Frank Herbert")
    void hasDune() { assertNotNull(DatabaseManager.getInstance().getByISBN("9780441013593")); }

    @Test @Order(27) @DisplayName("Book exists: A Brief History of Time")
    void hasBriefHistory() { assertNotNull(DatabaseManager.getInstance().getByISBN("9780553380163")); }

    // ═══ STOCK STATUS DIVERSITY ══════════════════════════════════════════════

    @Test @Order(30) @DisplayName("Status: has out-of-stock books (stock=0)")
    void hasOutOfStock() {
        assertTrue(DatabaseManager.getInstance().getAllBooks().stream()
            .anyMatch(b -> b.getStockCount() == 0));
    }

    @Test @Order(31) @DisplayName("Status: has low-stock books (stock <= threshold)")
    void hasLowStock() {
        assertTrue(DatabaseManager.getInstance().getAllBooks().stream()
            .anyMatch(b -> b.getStockCount() > 0 && b.needsRestock()));
    }

    @Test @Order(32) @DisplayName("Status: has well-stocked books (stock > threshold)")
    void hasWellStocked() {
        assertTrue(DatabaseManager.getInstance().getAllBooks().stream()
            .anyMatch(b -> b.getStockCount() > b.getRestockThreshold()));
    }

    // ═══ DEMO SALES ══════════════════════════════════════════════════════════

    @Test @Order(40) @DisplayName("Sales: at least 25 demo transactions exist")
    void salesCount() {
        List<Object[]> txns = DatabaseManager.getInstance().getTransactionHistory(100);
        assertTrue(txns.size() >= 25, "Expected 25+ demo sales, got " + txns.size());
    }

    @Test @Order(41) @DisplayName("Sales: demo sales have receipt content stored")
    void salesHaveReceipts() {
        List<Object[]> txns = DatabaseManager.getInstance().getTransactionHistory(10);
        int withReceipt = 0;
        for (Object[] t : txns) {
            String receipt = DatabaseManager.getInstance().getReceiptContent((String)t[0]);
            if (receipt != null && !receipt.isBlank()) withReceipt++;
        }
        assertTrue(withReceipt >= 5, "At least 5 of 10 recent sales should have receipts");
    }

    @Test @Order(42) @DisplayName("Sales: demo sales have line items")
    void salesHaveItems() {
        List<Object[]> txns = DatabaseManager.getInstance().getTransactionHistory(5);
        for (Object[] t : txns) {
            List<Object[]> items = DatabaseManager.getInstance().getSaleItems((String)t[0]);
            assertFalse(items.isEmpty(), "Sale " + t[0] + " has no line items");
        }
    }

    // ═══ OOS REQUESTS ════════════════════════════════════════════════════════

    @Test @Order(50) @DisplayName("OOS: demo requests exist")
    void oosCount() {
        assertTrue(DatabaseManager.getInstance().getAllOOSRequests().size() >= 5,
            "Should have at least 5 demo OOS requests");
    }

    @Test @Order(51) @DisplayName("OOS: out-of-stock books have request_count > 0")
    void oosRequestCounts() {
        BookCache.getInstance().invalidate();
        List<Book> oos = DatabaseManager.getInstance().getAllBooks().stream()
            .filter(b -> b.getStockCount() == 0).toList();
        assertTrue(oos.stream().anyMatch(b -> b.getRequestCount() > 0),
            "At least one out-of-stock book should have requests");
    }

    // ═══ USERS ═══════════════════════════════════════════════════════════════

    @Test @Order(60) @DisplayName("Users: owner1 exists")
    void ownerExists() { assertNotNull(DatabaseManager.getInstance().authenticate("owner1", "owner123")); }

    @Test @Order(61) @DisplayName("Users: manager1 exists")
    void managerExists() { assertNotNull(DatabaseManager.getInstance().authenticate("manager1", "mgr123")); }

    @Test @Order(62) @DisplayName("Users: clerk1 exists")
    void clerk1Exists() { assertNotNull(DatabaseManager.getInstance().authenticate("clerk1", "clerk123")); }

    @Test @Order(63) @DisplayName("Users: clerk2 exists")
    void clerk2Exists() { assertNotNull(DatabaseManager.getInstance().authenticate("clerk2", "clerk123")); }
}
