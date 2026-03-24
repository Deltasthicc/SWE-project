package bas.util;

/**
 * Validates ISBN-10 and ISBN-13 with full checksum verification.
 * Per SRS Data Validation Rules and FR-3.1.
 */
public class ISBNValidator {

    public static boolean isValid(String isbn) {
        if (isbn == null) return false;
        String s = clean(isbn);
        if (s.length() == 10) return checkISBN10(s);
        if (s.length() == 13) return checkISBN13(s);
        return false;
    }

    public static String clean(String isbn) {
        return isbn == null ? "" : isbn.replaceAll("[\\s\\-]", "");
    }

    public static String getValidationError(String isbn) {
        if (isbn == null || isbn.isBlank()) return "ISBN cannot be empty.";
        String s = clean(isbn);
        if (s.length() != 10 && s.length() != 13)
            return "ISBN must be 10 or 13 digits (got " + s.length() + ").";
        if (s.length() == 10 && !checkISBN10(s)) return "Invalid ISBN-10 checksum.";
        if (s.length() == 13 && !checkISBN13(s)) return "Invalid ISBN-13 checksum.";
        return null;
    }

    private static boolean checkISBN10(String s) {
        if (s.length() != 10) return false;
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
            sum += (s.charAt(i) - '0') * (10 - i);
        }
        char last = s.charAt(9);
        sum += (last == 'X' || last == 'x') ? 10
             : Character.isDigit(last) ? (last - '0') : -99;
        return sum % 11 == 0;
    }

    private static boolean checkISBN13(String s) {
        if (s.length() != 13) return false;
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
            sum += (s.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return sum % 10 == 0;
    }
}
