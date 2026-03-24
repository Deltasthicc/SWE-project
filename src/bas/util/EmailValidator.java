package bas.util;

import java.util.regex.Pattern;

public class EmailValidator {
    private static final Pattern P =
        Pattern.compile("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$");

    public static boolean isValid(String email) {
        return email != null && P.matcher(email.trim()).matches();
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public static String getValidationError(String email) {
        if (email == null || email.isBlank()) return "Email cannot be empty.";
        if (!isValid(email)) return "Invalid email format (e.g. user@example.com).";
        return null;
    }
}
