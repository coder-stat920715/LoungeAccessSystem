package com.lounge.access.util;

/** PAN helpers. Full card numbers must never be logged or persisted; use {@link #mask(String)}. */
public final class CardNumberUtil {

    private CardNumberUtil() {
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("\\D", "");
    }

    public static boolean isFullNumber(String digits) {
        return digits.length() >= 13 && digits.length() <= 19;
    }

    public static boolean isBin(String digits) {
        return digits.length() >= 6 && digits.length() <= 8;
    }

    public static String bin(String pan) {
        return pan.substring(0, Math.min(8, pan.length()));
    }

    public static String last4(String pan) {
        return pan.substring(Math.max(0, pan.length() - 4));
    }

    /** First six + last four digits visible (PCI-DSS display rule). */
    public static String mask(String digits) {
        if (isFullNumber(digits)) {
            return digits.substring(0, 6) + "*".repeat(digits.length() - 10) + last4(digits);
        }
        if (isBin(digits)) {
            return "BIN:" + digits;
        }
        return "INVALID";
    }
}
