package bas.test;

import bas.model.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Model Tests (Data Dictionary)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestModels {

    // ═══ BOOK ════════════════════════════════════════════════════════════════

    @Test @Order(1) @DisplayName("Book: constructor sets all fields")
    void bookConstructor() {
        Book b = new Book("9780451524935","1984","George Orwell","Signet Classic",
            "123 Publisher Row",199.0,"A-01",22,5,0,4.5,2);
        assertEquals("9780451524935", b.getIsbn());
        assertEquals("1984", b.getTitle());
        assertEquals("George Orwell", b.getAuthor());
        assertEquals("Signet Classic", b.getPublisher());
        assertEquals(199.0, b.getUnitPrice());
        assertEquals(22, b.getStockCount());
    }

    @Test @Order(2) @DisplayName("Book: isInStock true when stock > 0")
    void bookInStock() {
        Book b = new Book("1","T","A","P","",10.0,"",5,3,0,1.0,1);
        assertTrue(b.isInStock());
    }

    @Test @Order(3) @DisplayName("Book: isInStock false when stock = 0")
    void bookOutOfStock() {
        Book b = new Book("1","T","A","P","",10.0,"",0,3,0,1.0,1);
        assertFalse(b.isInStock());
    }

    @Test @Order(4) @DisplayName("Book: needsRestock true when stock <= threshold")
    void bookNeedsRestock() {
        Book b = new Book("1","T","A","P","",10.0,"",3,5,0,1.0,1);
        assertTrue(b.needsRestock());
    }

    @Test @Order(5) @DisplayName("Book: needsRestock false when stock > threshold")
    void bookDoesNotNeedRestock() {
        Book b = new Book("1","T","A","P","",10.0,"",10,5,0,1.0,1);
        assertFalse(b.needsRestock());
    }

    @Test @Order(6) @DisplayName("Book: procurement qty = max(0, ceil(weeklySales*leadTime) - stock)")
    void bookProcurementQty() {
        // weeklySales=4.5, leadTime=2 → ceil(9) - 22 = -13 → max(0,-13) = 0
        Book b = new Book("1","T","A","P","",10.0,"",22,5,0,4.5,2);
        assertEquals(0, b.getRequiredProcurementQty());
    }

    @Test @Order(7) @DisplayName("Book: procurement qty positive when stock is low")
    void bookProcurementQtyPositive() {
        // weeklySales=6.0, leadTime=2 → ceil(12) - 0 = 12
        Book b = new Book("1","T","A","P","",10.0,"",0,5,0,6.0,2);
        assertEquals(12, b.getRequiredProcurementQty());
    }

    @Test @Order(8) @DisplayName("Book: procurement qty with fractional weekly sales")
    void bookProcurementFractional() {
        // weeklySales=3.5, leadTime=3 → ceil(10.5)=11 - 2 = 9
        Book b = new Book("1","T","A","P","",10.0,"",2,5,0,3.5,3);
        assertEquals(9, b.getRequiredProcurementQty());
    }

    @Test @Order(9) @DisplayName("Book: setters update fields correctly")
    void bookSetters() {
        Book b = new Book();
        b.setIsbn("123"); b.setTitle("Test"); b.setAuthor("Auth");
        b.setPublisher("Pub"); b.setUnitPrice(100.0); b.setStockCount(10);
        assertEquals("123", b.getIsbn());
        assertEquals(100.0, b.getUnitPrice());
        assertEquals(10, b.getStockCount());
    }

    // ═══ LINE ITEM ═══════════════════════════════════════════════════════════

    @Test @Order(20) @DisplayName("LineItem: subtotal = qty * unitPrice")
    void lineItemSubtotal() {
        LineItem li = new LineItem("isbn1", "Book", 3, 199.0);
        assertEquals(597.0, li.getSubtotal(), 0.001);
    }

    @Test @Order(21) @DisplayName("LineItem: quantity updatable")
    void lineItemSetQty() {
        LineItem li = new LineItem("isbn1", "Book", 1, 100.0);
        li.setQuantity(5);
        assertEquals(5, li.getQuantity());
        assertEquals(500.0, li.getSubtotal(), 0.001);
    }

    // ═══ SALE RECORD ═════════════════════════════════════════════════════════

    @Test @Order(30) @DisplayName("SaleRecord: addItem increases total")
    void saleAddItem() {
        SaleRecord s = new SaleRecord("SALE-001", "clerk1");
        s.addItem(new LineItem("isbn1", "Book1", 2, 100.0));
        assertEquals(200.0, s.getTotalAmount(), 0.001);
        assertEquals(1, s.getItems().size());
    }

    @Test @Order(31) @DisplayName("SaleRecord: duplicate ISBN merges quantity")
    void saleMerge() {
        SaleRecord s = new SaleRecord("SALE-001", "clerk1");
        s.addItem(new LineItem("isbn1", "Book1", 2, 100.0));
        s.addItem(new LineItem("isbn1", "Book1", 3, 100.0));
        assertEquals(1, s.getItems().size());    // Still 1 line item
        assertEquals(5, s.getItems().get(0).getQuantity());  // Qty merged
        assertEquals(500.0, s.getTotalAmount(), 0.001);
    }

    @Test @Order(32) @DisplayName("SaleRecord: removeItem decreases total")
    void saleRemoveItem() {
        SaleRecord s = new SaleRecord("SALE-001", "clerk1");
        s.addItem(new LineItem("isbn1", "Book1", 1, 100.0));
        s.addItem(new LineItem("isbn2", "Book2", 1, 200.0));
        assertEquals(300.0, s.getTotalAmount(), 0.001);
        s.removeItem("isbn1");
        assertEquals(200.0, s.getTotalAmount(), 0.001);
        assertEquals(1, s.getItems().size());
    }

    @Test @Order(33) @DisplayName("SaleRecord: empty sale has zero total")
    void saleEmpty() {
        SaleRecord s = new SaleRecord("SALE-001", "clerk1");
        assertEquals(0.0, s.getTotalAmount(), 0.001);
        assertTrue(s.getItems().isEmpty());
    }

    @Test @Order(34) @DisplayName("SaleRecord: formatted timestamp is non-null")
    void saleTimestamp() {
        SaleRecord s = new SaleRecord("SALE-001", "clerk1");
        assertNotNull(s.getFormattedTimestamp());
        assertTrue(s.getFormattedTimestamp().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    // ═══ OOS REQUEST ═════════════════════════════════════════════════════════

    @Test @Order(40) @DisplayName("OOSRequest: default status is PENDING")
    void oosDefaultStatus() {
        OOSRequest req = new OOSRequest("REQ-001","isbn","Title","Author","Publisher","test@email.com");
        assertEquals(OOSRequest.Status.PENDING, req.getStatus());
    }

    @Test @Order(41) @DisplayName("OOSRequest: null email stored as null")
    void oosNullEmail() {
        OOSRequest req = new OOSRequest("REQ-001","isbn","Title","Author","Publisher",null);
        assertNull(req.getEmail());
    }

    @Test @Order(42) @DisplayName("OOSRequest: blank email stored as null")
    void oosBlankEmail() {
        OOSRequest req = new OOSRequest("REQ-001","isbn","Title","Author","Publisher","   ");
        assertNull(req.getEmail());
    }

    @Test @Order(43) @DisplayName("OOSRequest: status can be changed")
    void oosStatusChange() {
        OOSRequest req = new OOSRequest("REQ-001","isbn","Title","Author","Publisher","a@b.com");
        req.setStatus(OOSRequest.Status.NOTIFIED);
        assertEquals(OOSRequest.Status.NOTIFIED, req.getStatus());
    }

    // ═══ USER ════════════════════════════════════════════════════════════════

    @Test @Order(50) @DisplayName("User: all roles exist in enum")
    void userRoles() {
        assertDoesNotThrow(() -> User.Role.valueOf("CUSTOMER"));
        assertDoesNotThrow(() -> User.Role.valueOf("CLERK"));
        assertDoesNotThrow(() -> User.Role.valueOf("MANAGER"));
        assertDoesNotThrow(() -> User.Role.valueOf("OWNER"));
    }

    @Test @Order(51) @DisplayName("User: constructor and getters")
    void userConstructor() {
        User u = new User("owner1", "Ravi", "hash123", User.Role.OWNER);
        assertEquals("owner1", u.getUserId());
        assertEquals("Ravi", u.getName());
        assertEquals(User.Role.OWNER, u.getRole());
    }
}
