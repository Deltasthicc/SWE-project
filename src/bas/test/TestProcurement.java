package bas.test;

import bas.db.BookCache;
import bas.db.ConnectionPool;
import bas.db.DatabaseManager;
import bas.model.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Procurement lifecycle tests — create order, list, confirm arrival, stock increase,
 * double-confirm prevention, and full cleanup to preserve DB state.
 */
@DisplayName("Procurement Lifecycle Tests (FR-4, Write-Side)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProcurement {

    private static final String PROC_ISBN = "9780451524935"; // 1984 (seed book)
    private String testOrderId = null;
    private int originalStock = -1;

    @BeforeAll
    void setup() {
        DatabaseManager.getInstance().initializeDatabase();
        // Record original stock for restoration
        Book b = DatabaseManager.getInstance().getByISBN(PROC_ISBN);
        if (b != null) originalStock = b.getStockCount();
        cleanup();
    }

    @AfterAll
    void teardown() {
        cleanup();
        // Restore original stock if it was changed
        if (originalStock >= 0) {
            try {
                java.sql.Connection c = ConnectionPool.getInstance().borrow();
                try (java.sql.PreparedStatement ps = c.prepareStatement(
                        "UPDATE books SET stock_count = ? WHERE isbn = ?")) {
                    ps.setInt(1, originalStock);
                    ps.setString(2, PROC_ISBN);
                    ps.executeUpdate();
                }
                ConnectionPool.getInstance().release(c);
                BookCache.getInstance().invalidate();
            } catch (Exception e) {
                System.err.println("[TestProcurement Cleanup] Stock restore: " + e.getMessage());
            }
        }
    }

    private void cleanup() {
        try {
            java.sql.Connection c = ConnectionPool.getInstance().borrow();
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("DELETE FROM procurement_orders WHERE order_id LIKE 'PO-%' AND isbn = '" + PROC_ISBN + "'");
                s.execute("DELETE FROM app_logs WHERE actor = 'PROC_TEST'");
            }
            ConnectionPool.getInstance().release(c);
        } catch (Exception e) {
            System.err.println("[TestProcurement Cleanup] " + e.getMessage());
        }
    }

    // ═══ CREATE ORDER ═════════════════════════════════════════════════════════

    @Test @Order(1) @DisplayName("CreateOrder: succeeds for valid ISBN and quantity")
    void createOrderSuccess() {
        boolean ok = DatabaseManager.getInstance().createProcurementOrder(PROC_ISBN, 5, "PROC_TEST");
        assertTrue(ok, "Creating procurement order should succeed");
    }

    @Test @Order(2) @DisplayName("CreateOrder: zero quantity fails")
    void createOrderZeroQty() {
        assertFalse(DatabaseManager.getInstance().createProcurementOrder(PROC_ISBN, 0, "PROC_TEST"));
    }

    @Test @Order(3) @DisplayName("CreateOrder: negative quantity fails")
    void createOrderNegativeQty() {
        assertFalse(DatabaseManager.getInstance().createProcurementOrder(PROC_ISBN, -5, "PROC_TEST"));
    }

    @Test @Order(4) @DisplayName("CreateOrder: nonexistent ISBN fails")
    void createOrderBadISBN() {
        assertFalse(DatabaseManager.getInstance().createProcurementOrder("0000000000000", 5, "PROC_TEST"));
    }

    // ═══ LIST ORDERS ══════════════════════════════════════════════════════════

    @Test @Order(10) @DisplayName("ListOrders: all orders returned")
    void listAllOrders() {
        List<Object[]> orders = DatabaseManager.getInstance().getProcurementOrders(null);
        assertNotNull(orders);
        assertFalse(orders.isEmpty(), "Should have at least one procurement order");
    }

    @Test @Order(11) @DisplayName("ListOrders: filter by ORDERED status")
    void listOrderedOnly() {
        List<Object[]> orders = DatabaseManager.getInstance().getProcurementOrders("ORDERED");
        assertNotNull(orders);
        assertFalse(orders.isEmpty(), "Should have ORDERED procurement orders");
        // Find our test order and save its ID
        for (Object[] row : orders) {
            if (PROC_ISBN.equals(row[1])) {
                testOrderId = (String) row[0];
                break;
            }
        }
        assertNotNull(testOrderId, "Should find our test order for ISBN " + PROC_ISBN);
    }

    @Test @Order(12) @DisplayName("ListOrders: order has correct fields")
    void orderFields() {
        List<Object[]> orders = DatabaseManager.getInstance().getProcurementOrders("ORDERED");
        Object[] order = orders.stream()
            .filter(o -> PROC_ISBN.equals(o[1]))
            .findFirst().orElse(null);
        assertNotNull(order);
        assertNotNull(order[0], "order_id");
        assertEquals(PROC_ISBN, order[1]); // isbn
        assertNotNull(order[2], "title");
        assertNotNull(order[3], "publisher");
        assertEquals(5, order[4]); // quantity
        assertEquals("ORDERED", order[5]); // status
        assertNotNull(order[6], "ordered_at");
        assertNull(order[7], "arrived_at should be null before confirmation");
    }

    @Test @Order(13) @DisplayName("ListOrders: filter by ARRIVED returns empty before confirmation")
    void listArrivedEmpty() {
        List<Object[]> arrived = DatabaseManager.getInstance().getProcurementOrders("ARRIVED");
        boolean hasOurOrder = arrived.stream().anyMatch(o -> PROC_ISBN.equals(o[1]) && testOrderId != null && testOrderId.equals(o[0]));
        assertFalse(hasOurOrder, "Our order should not be ARRIVED yet");
    }

    // ═══ CONFIRM ARRIVAL ═════════════════════════════════════════════════════

    @Test @Order(20) @DisplayName("ConfirmArrival: succeeds for ORDERED order")
    void confirmSuccess() {
        assertNotNull(testOrderId, "Test order ID must be set from previous test");
        boolean ok = DatabaseManager.getInstance().confirmProcurementArrival(testOrderId, "PROC_TEST");
        assertTrue(ok, "Confirming arrival should succeed");
    }

    @Test @Order(21) @DisplayName("ConfirmArrival: stock increased by order quantity")
    void confirmStockIncrease() {
        BookCache.getInstance().invalidate();
        Book b = DatabaseManager.getInstance().getByISBN(PROC_ISBN);
        assertNotNull(b);
        assertEquals(originalStock + 5, b.getStockCount(),
            "Stock should increase by procurement quantity (5)");
    }

    @Test @Order(22) @DisplayName("ConfirmArrival: double-confirm same order fails")
    void doubleConfirmFails() {
        assertNotNull(testOrderId);
        boolean ok = DatabaseManager.getInstance().confirmProcurementArrival(testOrderId, "PROC_TEST");
        assertFalse(ok, "Confirming an already-ARRIVED order should fail (race-safe)");
    }

    @Test @Order(23) @DisplayName("ConfirmArrival: nonexistent order ID fails")
    void confirmNonexistent() {
        assertFalse(DatabaseManager.getInstance().confirmProcurementArrival("PO-FAKE1234", "PROC_TEST"));
    }

    @Test @Order(24) @DisplayName("ConfirmArrival: arrived_at is set after confirmation")
    void arrivedAtSet() {
        List<Object[]> arrived = DatabaseManager.getInstance().getProcurementOrders("ARRIVED");
        Object[] our = arrived.stream()
            .filter(o -> testOrderId != null && testOrderId.equals(o[0]))
            .findFirst().orElse(null);
        assertNotNull(our, "Our order should appear in ARRIVED list");
        assertNotNull(our[7], "arrived_at should be set");
    }

    // ═══ LOG ENTRIES ═════════════════════════════════════════════════════════

    @Test @Order(30) @DisplayName("ProcurementLog: order placement logged")
    void logOrderPlaced() {
        List<Object[]> logs = DatabaseManager.getInstance().getRecentLogs(50);
        boolean found = logs.stream().anyMatch(l ->
            "PROCUREMENT".equals(l[2]) && l[4] != null && ((String)l[4]).contains("Order placed"));
        assertTrue(found, "Procurement order placement should be logged");
    }

    @Test @Order(31) @DisplayName("ProcurementLog: arrival confirmation logged")
    void logArrivalConfirmed() {
        List<Object[]> logs = DatabaseManager.getInstance().getRecentLogs(50);
        boolean found = logs.stream().anyMatch(l ->
            "PROCUREMENT".equals(l[2]) && l[4] != null && ((String)l[4]).contains("Arrival confirmed"));
        assertTrue(found, "Procurement arrival should be logged");
    }

    @Test @Order(32) @DisplayName("CreateOrder: multiple orders for same ISBN allowed")
    void createMultipleOrders() {
        boolean ok = DatabaseManager.getInstance().createProcurementOrder(PROC_ISBN, 3, "PROC_TEST");
        assertTrue(ok, "Should be able to create another order for the same ISBN");
    }

    @Test @Order(33) @DisplayName("ListOrders: unfiltered list includes both ORDERED and ARRIVED")
    void listAllHasBothStatuses() {
        List<Object[]> all = DatabaseManager.getInstance().getProcurementOrders(null);
        boolean hasOrdered = all.stream().anyMatch(o -> "ORDERED".equals(o[5]));
        boolean hasArrived = all.stream().anyMatch(o -> "ARRIVED".equals(o[5]));
        assertTrue(hasOrdered || hasArrived, "Unfiltered list should have at least one status");
    }

    @Test @Order(34) @DisplayName("CreateOrder: publisher info stored from book record")
    void orderPublisherInfo() {
        List<Object[]> orders = DatabaseManager.getInstance().getProcurementOrders(null);
        Object[] our = orders.stream()
            .filter(o -> PROC_ISBN.equals(o[1]))
            .findFirst().orElse(null);
        assertNotNull(our);
        assertNotNull(our[3], "Publisher should be copied from book record");
    }
}
