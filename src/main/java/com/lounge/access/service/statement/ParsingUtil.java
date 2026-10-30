package com.lounge.access.service.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Locale-tolerant date and amount parsing for Indian card statements (day-first dates, lakh grouping, Dr/Cr). */
final class ParsingUtil {

    /** {@code debit} is null when the text carries no Dr/Cr/sign information. */
    record ParsedAmount(BigDecimal value, Boolean debit) {
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = Stream.of(
                    "d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "uuuu-M-d", "d/M/uu", "d-M-uu", "d.M.uu",
                    "d MMM uuuu", "d-MMM-uuuu", "d MMM uu", "d-MMM-uu", "d MMMM uuuu", "MMM d, uuuu")
            .map(p -> new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p)
                    .toFormatter(Locale.ENGLISH))
            .toList();

    private static final Pattern TIME_SUFFIX =
            Pattern.compile("\\s+\\d{1,2}:\\d{2}(:\\d{2})?(\\s*[AaPp][Mm])?$");
    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?");

    private ParsingUtil() {
    }

    static LocalDate parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replaceAll("\\s+", " ").replaceAll("(?i)\\bsept\\b", "Sep");
        s = TIME_SUFFIX.matcher(s).replaceFirst("");
        if (s.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    /** Accepts "1,23,456.00", "Rs. 450", "450.00 Cr", "(450.00)", "-450", "INR 450 Dr". Returns null if no number. */
    static ParsedAmount parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        String upper = s.toUpperCase(Locale.ROOT).replaceAll("\\.$", "");
        boolean negative = s.startsWith("-") || (s.startsWith("(") && s.endsWith(")"));
        Boolean debit = null;
        if (upper.endsWith("CR") || upper.startsWith("CR ")) {
            debit = false;
        } else if (upper.endsWith("DR") || upper.startsWith("DR ")) {
            debit = true;
        }
        String cleaned = s.replaceAll("(?i)(rs\\.?|inr|\u20B9|\\bcr\\b\\.?|\\bdr\\b\\.?|cr$|dr$)", "")
                .replaceAll("[,\\s()+\\-]", "");
        if (!NUMBER.matcher(cleaned).matches()) {
            return null;
        }
        if (negative && debit == null) {
            debit = false;
        }
        return new ParsedAmount(new BigDecimal(cleaned), debit);
    }

    static boolean looksLikeRefund(String description) {
        return Pattern.compile("(?i)refund|revers|cashback|waiver|credit adjust|chargeback|cancel").matcher(description).find();
    }

    static boolean looksLikePayment(String description) {
        return Pattern.compile("(?i)payment|thank you|autopay|auto debit|neft|imps|bill pay|received").matcher(description).find();
    }
}
