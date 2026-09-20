package com.example.ikhaya_accomadationapp;

import android.util.Base64;

import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class IdSecurityHelper {

    private static final String AES_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    // Hardcoded static key (32 bytes for AES-256) so the backend/admin can decrypt it.
    private static final String SHARED_SECRET_STRING = "iKaya_Secure_Key_2026_Shared_123";
    private static SecretKeySpec secretKey;

    static {
        try {
            // Convert the hardcoded string into a SecretKeySpec
            byte[] keyBytes = SHARED_SECRET_STRING.getBytes("UTF-8");
            secretKey = new SecretKeySpec(keyBytes, AES_ALGORITHM);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 1. Luhn Checksum Algorithm for 13-digit string.
     */
    public static boolean isValidLuhn(String id) {
        if (id == null || id.length() != 13 || !id.matches("\\d+")) {
            return false;
        }

        int sum = 0;
        boolean alternate = false;

        for (int i = id.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(id.substring(i, i + 1));
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

    /**
     * 2. Extracts first 6 digits, strict validates date, and calculates age.
     */
    public static int getAgeAndValidateDate(String id) {
        if (id == null || id.length() < 6) {
            return -1;
        }

        String dobString = id.substring(0, 6);
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd", Locale.getDefault());
        sdf.setLenient(false); // Strict date checking (e.g. catches Feb 29 on non-leap years)

        try {
            Date dob = sdf.parse(dobString);
            if (dob == null) return -1;

            Calendar dobCal = Calendar.getInstance();
            dobCal.setTime(dob);

            // Handle YY century wrapping issue (South African IDs only have 2 digit years)
            // If the ID year is less than or equal to the current 2-digit year, treat it as 2000+.
            // If it is greater, treat it as 1900+.
            Calendar today = Calendar.getInstance();
            int currentYear = today.get(Calendar.YEAR);
            int currentTwoDigitYear = currentYear % 100;
            
            int idYear = Integer.parseInt(dobString.substring(0, 2));
            
            int actualBirthYear;
            if (idYear <= currentTwoDigitYear) {
                actualBirthYear = 2000 + idYear;
            } else {
                actualBirthYear = 1900 + idYear;
            }
            
            dobCal.set(Calendar.YEAR, actualBirthYear);

            int age = today.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR);

            if (today.get(Calendar.DAY_OF_YEAR) < dobCal.get(Calendar.DAY_OF_YEAR)) {
                age--;
            }

            return age;

        } catch (ParseException e) {
            return -1; // Invalid date
        }
    }

    /**
     * 3. Encrypts string using AES/GCM/NoPadding.
     */
    public static String encryptId(String plainTextId) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainTextId.getBytes("UTF-8"));

            // Combine IV and cipherText
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.encodeToString(combined, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 4. Decrypts Base64 encrypted string using AES/GCM/NoPadding.
     */
    public static String decryptId(String base64EncryptedId) {
        try {
            byte[] combined = Base64.decode(base64EncryptedId, Base64.DEFAULT);

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            byte[] cipherText = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}