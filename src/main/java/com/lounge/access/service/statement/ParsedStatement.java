package com.lounge.access.service.statement;

import java.time.LocalDate;
import java.util.List;

public record ParsedStatement(String fileName, String format, List<StatementTransaction> transactions,
                              int skippedLines) {

    public LocalDate firstDate() {
        return transactions.stream().map(StatementTransaction::date).min(LocalDate::compareTo).orElse(null);
    }

    public LocalDate lastDate() {
        return transactions.stream().map(StatementTransaction::date).max(LocalDate::compareTo).orElse(null);
    }
}
