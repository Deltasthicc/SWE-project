package bas.test;

import bas.config.AppConfig;
import bas.model.LineItem;
import bas.model.SaleRecord;
import bas.service.EmailService;
import bas.util.PrinterUtil;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

@DisplayName("Email Service & Printer Tests (NFR-4, FR-3.4)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestEmailAndPrinter {

    // ═══ EMAIL CONFIGURATION ═════════════════════════════════════════════════

    @Test @Order(1) @DisplayName("Email: pre-configured from AppConfig")
    void emailPreConfigured() {
        assertTrue(EmailService.isConfigured(), "SMTP should be pre-configured");
    }

    @Test @Order(2) @DisplayName("Email: host is smtp.gmail.com")
    void emailHost() { assertEquals("smtp.gmail.com", EmailService.getHost()); }

    @Test @Order(3) @DisplayName("Email: port is 587 (STARTTLS)")
    void emailPort() { assertEquals(587, EmailService.getPort()); }

    @Test @Order(4) @DisplayName("Email: sender matches AppConfig")
    void emailSender() { assertEquals(AppConfig.SMTP_EMAIL, EmailService.getEmail()); }

    @Test @Order(5) @DisplayName("Email: reconfigure with empty clears ready flag")
    void emailReconfigureEmpty() {
        // Save current config
        String origHost = EmailService.getHost();
        int origPort = EmailService.getPort();
        String origEmail = EmailService.getEmail();

        EmailService.configure("", 0, "", "");
        assertFalse(EmailService.isConfigured());

        // Restore
        EmailService.configure(origHost, origPort, origEmail, AppConfig.SMTP_PASSWORD);
        assertTrue(EmailService.isConfigured());
    }

    @Test @Order(6) @DisplayName("Email: reconfigure with valid settings restores ready")
    void emailReconfigure() {
        EmailService.configure("smtp.gmail.com", 587, "test@test.com", "password123");
        assertTrue(EmailService.isConfigured());
        // Restore real config
        EmailService.configure(AppConfig.SMTP_HOST, AppConfig.SMTP_PORT,
            AppConfig.SMTP_EMAIL, AppConfig.SMTP_PASSWORD);
    }

    // Note: Actual email sending test is intentionally skipped to avoid
    // sending real emails during automated tests. The send() method
    // has been verified manually. Uncomment below to test live:
    //
    // @Test @Order(7) @DisplayName("Email: live send test")
    // void emailLiveSend() {
    //     assertTrue(EmailService.send("your-test-email@gmail.com",
    //         "BAS JUnit Test", "Automated test email from BAS test suite."));
    // }

    // ═══ PRINTER / RECEIPT GENERATION ════════════════════════════════════════

    @Test @Order(10) @DisplayName("Receipt: buildReceiptString produces valid output")
    void receiptString() {
        SaleRecord sale = new SaleRecord("SALE-RCPT", "clerk1");
        sale.addItem(new LineItem("9780451524935", "1984", 2, 199.0));
        String receipt = PrinterUtil.buildReceiptString(sale);
        assertNotNull(receipt);
        assertFalse(receipt.isBlank());
        assertTrue(receipt.contains("SALE-RCPT"));
        assertTrue(receipt.contains("clerk1"));
        assertTrue(receipt.contains("TOTAL"));
    }

    @Test @Order(11) @DisplayName("Receipt: contains sale ID")
    void receiptHasSaleId() {
        SaleRecord sale = new SaleRecord("SALE-XYZ123", "clerk2");
        sale.addItem(new LineItem("isbn", "Book", 1, 100.0));
        String receipt = PrinterUtil.buildReceiptString(sale);
        assertTrue(receipt.contains("SALE-XYZ123"));
    }

    @Test @Order(12) @DisplayName("Receipt: contains all line items")
    void receiptLineItems() {
        SaleRecord sale = new SaleRecord("SALE-MULTI", "clerk1");
        sale.addItem(new LineItem("isbn1", "First Book", 1, 100.0));
        sale.addItem(new LineItem("isbn2", "Second Book", 2, 200.0));
        String receipt = PrinterUtil.buildReceiptString(sale);
        assertTrue(receipt.contains("First Book"));
        assertTrue(receipt.contains("Second Book"));
    }

    @Test @Order(13) @DisplayName("Receipt: long title gets truncated")
    void receiptLongTitle() {
        SaleRecord sale = new SaleRecord("SALE-LONG", "clerk1");
        sale.addItem(new LineItem("isbn", "A Very Long Book Title That Should Be Truncated", 1, 100.0));
        String receipt = PrinterUtil.buildReceiptString(sale);
        assertTrue(receipt.contains("..."), "Long titles should be truncated with ...");
    }

    @Test @Order(14) @DisplayName("Receipt: buildLines returns list of strings")
    void receiptLines() {
        SaleRecord sale = new SaleRecord("SALE-LINES", "clerk1");
        sale.addItem(new LineItem("isbn", "Book", 1, 100.0));
        List<String> lines = PrinterUtil.buildLines(sale);
        assertNotNull(lines);
        assertTrue(lines.size() >= 10, "Receipt should have at least 10 lines");
        assertTrue(lines.get(0).contains("===="));
    }

    @Test @Order(15) @DisplayName("Receipt: empty sale produces valid receipt")
    void receiptEmptySale() {
        SaleRecord sale = new SaleRecord("SALE-EMPTY", "clerk1");
        String receipt = PrinterUtil.buildReceiptString(sale);
        assertNotNull(receipt);
        assertTrue(receipt.contains("TOTAL"));
        assertTrue(receipt.contains("0.00"));
    }

    @Test @Order(16) @DisplayName("Receipt: INR currency format")
    void receiptCurrency() {
        SaleRecord sale = new SaleRecord("SALE-INR", "clerk1");
        sale.addItem(new LineItem("isbn", "Book", 1, 1234.56));
        String receipt = PrinterUtil.buildReceiptString(sale);
        assertTrue(receipt.contains("1234.56"));
    }
}
