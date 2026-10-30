package com.lounge.access.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;

/** Formats rupee amounts with Indian digit grouping, e.g. 5000000 -> "₹50,00,000". Rounded to whole rupees. */
public final class InrFormat {

    private InrFormat() {
    }

    public static String format(BigDecimal amount) {
        BigDecimal rounded = amount.setScale(0, RoundingMode.HALF_UP);
        String digits = rounded.abs().toPlainString();
        String grouped;
        if (digits.length() <= 3) {
            grouped = digits;
        } else {
            String last3 = digits.substring(digits.length() - 3);
            String rest = digits.substring(0, digits.length() - 3);
            Deque<String> parts = new ArrayDeque<>();
            for (int end = rest.length(); end > 0; end -= 2) {
                parts.addFirst(rest.substring(Math.max(0, end - 2), end));
            }
            grouped = String.join(",", parts) + "," + last3;
        }
        return (rounded.signum() < 0 ? "-" : "") + "\u20B9" + grouped;
    }
}
