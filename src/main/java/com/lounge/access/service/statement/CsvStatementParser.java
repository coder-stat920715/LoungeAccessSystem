package com.lounge.access.service.statement;

import com.lounge.access.dto.StatementCategory;
import com.lounge.access.dto.TransactionKind;
import com.lounge.access.service.statement.ParsingUtil.ParsedAmount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Parses bank-exported CSV statements. Tolerates preamble lines before the table, comma/semicolon/tab/pipe
 * delimiters, quoted cells, and either a single signed/Dr-Cr amount column or separate debit/credit columns.
 */
@Component
public class CsvStatementParser {

    private static final int HEADER_SEARCH_LIMIT = 60;

    private final TransactionCategorizer categorizer;

    public CsvStatementParser(TransactionCategorizer categorizer) {
        this.categorizer = categorizer;
    }

    private record Columns(int date, int description, int category, int type, int amount, int debit, int credit) {
    }

    public ParsedStatement parse(String fileName, String content) {
        String text = content.startsWith("\uFEFF") ? content.substring(1) : content;
        List<List<String>> rows = tokenize(text, detectDelimiter(text));

        int headerIndex = -1;
        Columns columns = null;
        for (int i = 0; i < Math.min(rows.size(), HEADER_SEARCH_LIMIT); i++) {
            Columns candidate = mapHeader(rows.get(i));
            if (candidate != null) {
                headerIndex = i;
                columns = candidate;
                break;
            }
        }
        if (columns == null) {
            throw new StatementParseException(422, "Could not find a transaction table header in " + fileName
                    + ". Expected columns such as Date, Description/Transaction Details and Amount (or Debit/Credit).");
        }

        List<StatementTransaction> transactions = new ArrayList<>();
        int skipped = 0;
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlank(row)) {
                continue;
            }
            LocalDate date = ParsingUtil.parseDate(cell(row, columns.date()));
            String description = cell(row, columns.description()).trim();
            StatementTransaction tx = date == null ? null : buildTransaction(fileName, date, description, row, columns);
            if (tx == null) {
                if (row.size() >= 3) {
                    skipped++;
                }
                continue;
            }
            transactions.add(tx);
        }
        return new ParsedStatement(fileName, "CSV", transactions, skipped);
    }

    private StatementTransaction buildTransaction(String fileName, LocalDate date, String description,
                                                  List<String> row, Columns c) {
        BigDecimal amount;
        boolean debit;
        if (c.amount() >= 0) {
            ParsedAmount parsed = ParsingUtil.parseAmount(cell(row, c.amount()));
            if (parsed == null) {
                return null;
            }
            amount = parsed.value();
            Boolean typeDebit = debitFromType(cell(row, c.type()));
            debit = typeDebit != null ? typeDebit : parsed.debit() != null ? parsed.debit() : Boolean.TRUE;
        } else {
            ParsedAmount debitAmount = c.debit() >= 0 ? ParsingUtil.parseAmount(cell(row, c.debit())) : null;
            ParsedAmount creditAmount = c.credit() >= 0 ? ParsingUtil.parseAmount(cell(row, c.credit())) : null;
            if (debitAmount != null && debitAmount.value().signum() > 0) {
                amount = debitAmount.value();
                debit = true;
            } else if (creditAmount != null && creditAmount.value().signum() > 0) {
                amount = creditAmount.value();
                debit = false;
            } else {
                return null;
            }
        }
        if (amount.signum() == 0) {
            return null;
        }
        String rawCategory = c.category() >= 0 ? cell(row, c.category()) : null;
        TransactionKind kind = debit ? TransactionKind.PURCHASE
                : ParsingUtil.looksLikeRefund(description) ? TransactionKind.REFUND : TransactionKind.PAYMENT;
        StatementCategory category = kind == TransactionKind.PURCHASE
                ? categorizer.categorize(description, rawCategory) : StatementCategory.OTHER;
        return new StatementTransaction(date, description, amount, kind, category, fileName);
    }

    private static Boolean debitFromType(String type) {
        String t = type.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) {
            return null;
        }
        if (t.startsWith("dr") || t.startsWith("debit") || t.equals("d")) {
            return true;
        }
        if (t.startsWith("cr") || t.startsWith("credit") || t.equals("c")) {
            return false;
        }
        return null;
    }

    private Columns mapHeader(List<String> row) {
        List<String> h = row.stream().map(CsvStatementParser::norm).toList();
        int type = indexOfExact(h, "dr/cr", "cr/dr", "type", "txn type", "transaction type", "debit/credit",
                "debit credit");
        int date = -1;
        for (int i = 0; i < h.size() && date < 0; i++) {
            String v = h.get(i);
            if (v.contains("date") && !v.contains("due") && !v.contains("statement") && !v.contains("value")) {
                date = i;
            }
        }
        if (date < 0) {
            date = indexOfContains(h, -1, "date");
        }
        int description = -1;
        for (String keyword : List.of("description", "details", "narration", "particular", "merchant", "remarks",
                "transaction")) {
            for (int i = 0; i < h.size() && description < 0; i++) {
                String v = h.get(i);
                if (v.contains(keyword) && !v.contains("date") && !v.contains("category") && i != type) {
                    description = i;
                }
            }
            if (description >= 0) {
                break;
            }
        }
        int category = indexOfContains(h, -1, "category");
        int amount = -1;
        for (int i = 0; i < h.size() && amount < 0; i++) {
            String v = h.get(i);
            if (v.contains("amount") && !v.contains("balance") && !v.contains("debit") && !v.contains("credit")) {
                amount = i;
            }
        }
        int debit = -1;
        int credit = -1;
        for (int i = 0; i < h.size(); i++) {
            String v = h.get(i);
            if (v.contains("debit") && !v.contains("credit") && !v.contains("/") && debit < 0) {
                debit = i;
            }
            if (v.contains("credit") && !v.contains("debit") && !v.contains("card") && !v.contains("/")
                    && credit < 0) {
                credit = i;
            }
        }
        boolean hasAmount = amount >= 0 || debit >= 0 || credit >= 0;
        if (date < 0 || description < 0 || !hasAmount) {
            return null;
        }
        if (amount >= 0) {
            debit = -1;
            credit = -1;
        }
        return new Columns(date, description, category, type, amount, debit, credit);
    }

    private static int indexOfExact(List<String> h, String... names) {
        for (int i = 0; i < h.size(); i++) {
            for (String name : names) {
                if (h.get(i).equals(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int indexOfContains(List<String> h, int fallback, String token) {
        for (int i = 0; i < h.size(); i++) {
            if (h.get(i).contains(token)) {
                return i;
            }
        }
        return fallback;
    }

    private static String norm(String header) {
        return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/ ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String cell(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }

    private static boolean isBlank(List<String> row) {
        return row.stream().allMatch(c -> c.trim().isEmpty());
    }

    static char detectDelimiter(String text) {
        String sample = text.lines().filter(l -> !l.isBlank()).limit(40).reduce("", (a, b) -> a + "\n" + b);
        char best = ',';
        int bestCount = 0;
        for (char d : new char[] {',', ';', '\t', '|'}) {
            int count = 0;
            boolean inQuotes = false;
            for (char ch : sample.toCharArray()) {
                if (ch == '"') {
                    inQuotes = !inQuotes;
                } else if (ch == d && !inQuotes) {
                    count++;
                }
            }
            if (count > bestCount) {
                best = d;
                bestCount = count;
            }
        }
        return best;
    }

    /** RFC-4180 style tokenizer (quoted cells, escaped quotes, embedded newlines). */
    static List<List<String>> tokenize(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == delimiter) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
