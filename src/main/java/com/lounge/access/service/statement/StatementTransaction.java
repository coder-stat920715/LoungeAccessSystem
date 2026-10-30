package com.lounge.access.service.statement;

import com.lounge.access.dto.StatementCategory;
import com.lounge.access.dto.TransactionKind;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One statement line. {@code amount} is always positive; {@code kind} tells whether it is a debit or a credit. */
public record StatementTransaction(LocalDate date, String description, BigDecimal amount, TransactionKind kind,
                                   StatementCategory category, String sourceFile) {
}
