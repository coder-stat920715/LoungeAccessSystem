package com.lounge.access.service.statement;

import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardType;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * @param cardIssueDate optional; enables the Axis new-card exception check
 * @param visitsUsedInTargetQuarter lounge visits already used in the calendar quarter of the target month
 */
public record AnalysisRequest(Bank bank, CardType cardType, String productCode, LocalDate cardIssueDate,
                              YearMonth targetMonth, int visitsUsedInTargetQuarter) {
}
