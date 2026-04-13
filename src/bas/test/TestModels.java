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

    // ═══ ADDITIONAL MODEL TESTS ═════════════════════════════════════════════

    @Test @Order(60) @DisplayName("User: setters update all fields")
    void userSetters() {
        User u = new User("id1", "Name1", "hash1", User.Role.CLERK);
        u.setUserId("id2"); u.setName("Name2");
        u.setPasswordHash("hash2"); u.setRole(User.Role.OWNER);
        assertEquals("id2", u.getUserId());
        assertEquals("Name2", u.getName());
        assertEquals("hash2", u.getPasswordHash());
        assertEquals(User.Role.OWNER, u.getRole());
    }

    @Test @Order(61) @DisplayName("OOSRequest: setters update all fields")
    void oosSetters() {
        OOSRequest req = new OOSRequest("REQ-1","isbn1","T","A","P","e@m.com");
        req.setRequestId("REQ-2"); req.setIsbn("isbn2");
        req.setTitle("T2"); req.setAuthor("A2");
        req.setPublisher("P2"); req.setEmail("new@m.com");
        assertEquals("REQ-2", req.getRequestId());
        assertEquals("isbn2", req.getIsbn());
        assertEquals("T2", req.getTitle());
        assertEquals("A2", req.getAuthor());
        assertEquals("P2", req.getPublisher());
        assertEquals("new@m.com", req.getEmail());
    }

    @Test @Order(62) @DisplayName("OOSRequest: DB constructor preserves stored timestamp")
    void oosDbTimestamp() {
        OOSRequest req = new OOSRequest("REQ-1","isbn","T","A","P","e@m.com","2026-01-15 10:30:00");
        assertEquals("2026-01-15 10:30:00", req.getFormattedTimestamp());
    }

    @Test @Order(63) @DisplayName("OOSRequest: invalid stored timestamp falls back to now")
    void oosInvalidTimestamp() {
        OOSRequest req = new OOSRequest("REQ-1","isbn","T","A","P","e@m.com","not-a-date");
        assertNotNull(req.getFormattedTimestamp());
        // Should be a valid timestamp (fell back to now)
        assertTrue(req.getFormattedTimestamp().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test @Order(64) @DisplayName("OOSRequest: setTimestamp updates correctly")
    void oosSetTimestamp() {
        OOSRequest req = new OOSRequest("REQ-1","isbn","T","A","P",null);
        java.time.LocalDateTime custom = java.time.LocalDateTime.of(2026,6,15,12,0,0);
        req.setTimestamp(custom);
        assertEquals("2026-06-15 12:00:00", req.getFormattedTimestamp());
    }

    @Test @Order(65) @DisplayName("SaleRecord: setClerkId updates correctly")
    void saleSetClerkId() {
        SaleRecord s = new SaleRecord("SALE-1", "clerk1");
        s.setClerkId("clerk2");
        assertEquals("clerk2", s.getClerkId());
    }

    @Test @Order(66) @DisplayName("SaleRecord: setSaleId updates correctly")
    void saleSetSaleId() {
        SaleRecord s = new SaleRecord("SALE-OLD", "clerk1");
        s.setSaleId("SALE-NEW");
        assertEquals("SALE-NEW", s.getSaleId());
    }

    @Test @Order(67) @DisplayName("SaleRecord: setTimestamp updates correctly")
    void saleSetTimestamp() {
        SaleRecord s = new SaleRecord("SALE-1", "clerk1");
        java.time.LocalDateTime custom = java.time.LocalDateTime.of(2026,3,1,9,0,0);
        s.setTimestamp(custom);
        assertEquals("2026-03-01 09:00:00", s.getFormattedTimestamp());
    }

    @Test @Order(68) @DisplayName("Book: all remaining getters return constructor values")
    void bookAllGetters() {
        Book b = new Book("isbn","T","A","P","Addr",250.0,"B-05",15,7,3,4.5,3);
        assertEquals("Addr", b.getPublisherAddress());
        assertEquals("B-05", b.getRackLocation());
        assertEquals(7, b.getRestockThreshold());
        assertEquals(3, b.getRequestCount());
        assertEquals(4.5, b.getWeeklySales(), 0.001);
        assertEquals(3, b.getProcurementLeadTimeWeeks());
    }

    @Test @Order(69) @DisplayName("Book: remaining setters update correctly")
    void bookRemainingSetters() {
        Book b = new Book();
        b.setPublisherAddress("Addr"); b.setRackLocation("C-01");
        b.setRestockThreshold(10); b.setRequestCount(5);
        b.setWeeklySales(3.5); b.setProcurementLeadTimeWeeks(4);
        assertEquals("Addr", b.getPublisherAddress());
        assertEquals("C-01", b.getRackLocation());
        assertEquals(10, b.getRestockThreshold());
        assertEquals(5, b.getRequestCount());
        assertEquals(3.5, b.getWeeklySales(), 0.001);
        assertEquals(4, b.getProcurementLeadTimeWeeks());
    }
}
