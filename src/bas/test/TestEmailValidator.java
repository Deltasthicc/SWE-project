package bas.test;

import bas.util.EmailValidator;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Email Validator Tests (Data Validation Rules)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestEmailValidator {

    @Test @Order(1) @DisplayName("Valid: standard email")
    void validStandard() { assertTrue(EmailValidator.isValid("user@example.com")); }

    @Test @Order(2) @DisplayName("Valid: email with dots")
    void validDots() { assertTrue(EmailValidator.isValid("first.last@domain.co.in")); }

    @Test @Order(3) @DisplayName("Valid: email with plus")
    void validPlus() { assertTrue(EmailValidator.isValid("user+tag@gmail.com")); }

    @Test @Order(4) @DisplayName("Valid: email with underscore")
    void validUnderscore() { assertTrue(EmailValidator.isValid("user_name@company.org")); }

    @Test @Order(5) @DisplayName("Valid: email with hyphen in domain")
    void validHyphenDomain() { assertTrue(EmailValidator.isValid("a@my-domain.com")); }

    @Test @Order(6) @DisplayName("Invalid: no @ symbol")
    void noAtSymbol() { assertFalse(EmailValidator.isValid("userdomain.com")); }

    @Test @Order(7) @DisplayName("Invalid: no domain")
    void noDomain() { assertFalse(EmailValidator.isValid("user@")); }

    @Test @Order(8) @DisplayName("Invalid: no TLD")
    void noTLD() { assertFalse(EmailValidator.isValid("user@domain")); }

    @Test @Order(9) @DisplayName("Invalid: double @ sign")
    void doubleAt() { assertFalse(EmailValidator.isValid("user@@domain.com")); }

    @Test @Order(10) @DisplayName("Invalid: spaces in email")
    void spacesInEmail() { assertFalse(EmailValidator.isValid("user @domain.com")); }

    @Test @Order(11) @DisplayName("Invalid: empty string")
    void emptyEmail() { assertFalse(EmailValidator.isValid("")); }

    @Test @Order(12) @DisplayName("Invalid: null")
    void nullEmail() { assertFalse(EmailValidator.isValid(null)); }

    @Test @Order(13) @DisplayName("Normalize trims and lowercases")
    void normalize() { assertEquals("test@email.com", EmailValidator.normalize("  Test@EMAIL.COM  ")); }

    @Test @Order(14) @DisplayName("Normalize null returns null")
    void normalizeNull() { assertNull(EmailValidator.normalize(null)); }

    @Test @Order(15) @DisplayName("Validation error for blank")
    void errorBlank() {
        String err = EmailValidator.getValidationError("  ");
        assertNotNull(err);
        assertTrue(err.toLowerCase().contains("empty") || err.toLowerCase().contains("blank"));
    }

    @Test @Order(16) @DisplayName("Validation error for invalid format")
    void errorInvalid() {
        String err = EmailValidator.getValidationError("not-an-email");
        assertNotNull(err);
        assertTrue(err.toLowerCase().contains("invalid"));
    }

    @Test @Order(17) @DisplayName("No error for valid email")
    void noError() { assertNull(EmailValidator.getValidationError("valid@email.com")); }
}
