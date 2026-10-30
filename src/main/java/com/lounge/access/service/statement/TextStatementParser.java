package com.lounge.access.service.statement;

import com.lounge.access.dto.StatementCategory;
import com.lounge.access.dto.TransactionKind;
import com.lounge.access.service.statement.ParsingUtil.ParsedAmount;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Best-effort parser for text statements and text extracted from PDFs. A transaction line looks like
 * {@code 03/08/2026 AMAZON PAY INDIA MUMBAI 6,200.00 Dr}. Lines that start with a date but do not fit the
 * pattern are counted as skipped so the caller can warn that the parse may be incomplete.
 */
@Component
public class TextStatementParser {

    private static final String DATE =
            "(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{2,4}|\\d{1,2}[ \\-][A-Za-z]{3,9}[ \\-,]+\\d{2,4})";
    private static final Pattern LINE = Pattern.compile("^\\s*" + DATE
            + "\\s+(.+?)\\s+(?:(?:Rs\\.?|INR|\u20B9)\\s*)?(-?\\(?[\\d,]+\\.\\d{2}\\)?)\\s*(Cr|Dr|CR|DR)?\\.?\\s*$");
    private static final Pattern STARTS_WITH_DATE = Pattern.compile("^\\s*" + DATE + "\\s+\\S");

    private final TransactionCategorizer categorizer;

    public TextStatementParser(TransactionCategorizer categorizer) {
        this.categorizer = categorizer;
    }

    public ParsedStatement parse(String fileName, String format, String content) {
        List<StatementTransaction> transactions = new ArrayList<>();
        int skipped = 0;
        for (String line : content.split("\\R")) {
            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                if (STARTS_WITH_DATE.matcher(line).find()) {
                    skipped++;
                }
                continue;
            }
            LocalDate date = ParsingUtil.parseDate(m.group(1));
            String description = m.group(2).trim();
            ParsedAmount amount = ParsingUtil.parseAmount(m.group(3) + (m.group(4) == null ? "" : " " + m.group(4)));
            if (date == null || amount == null || amount.value().signum() == 0) {
                skipped++;
                continue;
            }
            boolean debit = amount.debit() != null ? amount.debit()
                    : !(ParsingUtil.looksLikeRefund(description) || ParsingUtil.looksLikePayment(description));
            TransactionKind kind = debit ? TransactionKind.PURCHASE
                    : ParsingUtil.looksLikeRefund(description) ? TransactionKind.REFUND : TransactionKind.PAYMENT;
            StatementCategory category = kind == TransactionKind.PURCHASE
                    ? categorizer.categorize(description, null) : StatementCategory.OTHER;
            transactions.add(new StatementTransaction(date, description, amount.value(), kind, category, fileName));
        }
        return new ParsedStatement(fileName, format, transactions, skipped);
    }
}
