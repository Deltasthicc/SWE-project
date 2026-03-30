package bas.test;

import bas.util.ISBNValidator;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ISBN Validator Tests (FR-3.1, Data Validation Rules)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestISBNValidator {

    // ── Valid ISBN-13 ──────────────────────────────────────────────────────
    @Test @Order(1) @DisplayName("Valid ISBN-13: 9780451524935 (1984)")
    void validISBN13_1984() { assertTrue(ISBNValidator.isValid("9780451524935")); }

    @Test @Order(2) @DisplayName("Valid ISBN-13: 9781982173593 (Atomic Habits)")
    void validISBN13_AtomicHabits() { assertTrue(ISBNValidator.isValid("9781982173593")); }

    @Test @Order(3) @DisplayName("Valid ISBN-13: 9780590353427 (Harry Potter 1)")
    void validISBN13_HarryPotter() { assertTrue(ISBNValidator.isValid("9780590353427")); }

    @Test @Order(4) @DisplayName("Valid ISBN-13 with hyphens: 978-0-451-52493-5")
    void validISBN13WithHyphens() { assertTrue(ISBNValidator.isValid("978-0-451-52493-5")); }

    @Test @Order(5) @DisplayName("Valid ISBN-13 with spaces: 978 0451 524935")
    void validISBN13WithSpaces() { assertTrue(ISBNValidator.isValid("978 0451 524935")); }

    // ── Valid ISBN-10 ──────────────────────────────────────────────────────
    @Test @Order(6) @DisplayName("Valid ISBN-10: 0451524934")
    void validISBN10() { assertTrue(ISBNValidator.isValid("0451524934")); }

    @Test @Order(7) @DisplayName("Valid ISBN-10 ending in X: 080442957X")
    void validISBN10EndingX() { assertTrue(ISBNValidator.isValid("080442957X")); }

    @Test @Order(8) @DisplayName("Valid ISBN-10 with lowercase x: 080442957x")
    void validISBN10LowercaseX() { assertTrue(ISBNValidator.isValid("080442957x")); }

    // ── Invalid ISBNs ─────────────────────────────────────────────────────
    @Test @Order(9) @DisplayName("Invalid: wrong checksum ISBN-13")
    void invalidChecksum13() { assertFalse(ISBNValidator.isValid("9780451524936")); }

    @Test @Order(10) @DisplayName("Invalid: wrong checksum ISBN-10")
    void invalidChecksum10() { assertFalse(ISBNValidator.isValid("0451524935")); }

    @Test @Order(11) @DisplayName("Invalid: too short (5 digits)")
    void tooShort() { assertFalse(ISBNValidator.isValid("12345")); }

    @Test @Order(12) @DisplayName("Invalid: too long (15 digits)")
    void tooLong() { assertFalse(ISBNValidator.isValid("978045152493512")); }

    @Test @Order(13) @DisplayName("Invalid: 11 digits (between 10 and 13)")
    void elevenDigits() { assertFalse(ISBNValidator.isValid("97804515249")); }

    @Test @Order(14) @DisplayName("Invalid: letters in ISBN")
    void lettersInISBN() { assertFalse(ISBNValidator.isValid("978ABCD52493")); }

    @Test @Order(15) @DisplayName("Invalid: empty string")
    void emptyString() { assertFalse(ISBNValidator.isValid("")); }

    @Test @Order(16) @DisplayName("Invalid: null")
    void nullISBN() { assertFalse(ISBNValidator.isValid(null)); }

    @Test @Order(17) @DisplayName("Invalid: only whitespace")
    void whitespaceOnly() { assertFalse(ISBNValidator.isValid("   ")); }

    // ── Clean method ──────────────────────────────────────────────────────
    @Test @Order(18) @DisplayName("Clean removes hyphens and spaces")
    void cleanMethod() { assertEquals("9780451524935", ISBNValidator.clean("978-0451-524935")); }

    @Test @Order(19) @DisplayName("Clean handles null safely")
    void cleanNull() { assertEquals("", ISBNValidator.clean(null)); }

    // ── Validation error messages ─────────────────────────────────────────
    @Test @Order(20) @DisplayName("Error message for empty ISBN")
    void errorEmpty() { assertNotNull(ISBNValidator.getValidationError("")); }

    @Test @Order(21) @DisplayName("Error message for wrong length")
    void errorWrongLength() {
        String err = ISBNValidator.getValidationError("12345");
        assertNotNull(err);
        assertTrue(err.contains("10 or 13"));
    }

    @Test @Order(22) @DisplayName("Error message for bad checksum")
    void errorBadChecksum() {
        String err = ISBNValidator.getValidationError("9780451524936");
        assertNotNull(err);
        assertTrue(err.contains("checksum"));
    }

    @Test @Order(23) @DisplayName("No error for valid ISBN")
    void noErrorForValid() { assertNull(ISBNValidator.getValidationError("9780451524935")); }
}
