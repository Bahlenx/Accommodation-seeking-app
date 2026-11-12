package com.example.ikhaya_accommodation_seeking_app;

public class IdValidationUtils {
    public static boolean isValidSAID(String idNumber) {
        if (idNumber == null || !idNumber.matches("\\d{13}")) {
            return false;
        }

        // Validate Month (MM) and Day (DD)
        int month = Integer.parseInt(idNumber.substring(2, 4));
        int day = Integer.parseInt(idNumber.substring(4, 6));
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return false;
        }

        // Luhn Algorithm Check
        int sum = 0;
        boolean alternate = false;
        for (int i = idNumber.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(idNumber.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }
}
