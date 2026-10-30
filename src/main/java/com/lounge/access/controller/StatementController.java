package com.lounge.access.controller;

import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardType;
import com.lounge.access.dto.StatementAnalysisResponse;
import com.lounge.access.service.impl.StatementAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
@Tag(name = "Statement Analysis", description = "Forecast lounge eligibility from uploaded card statements")
public class StatementController {

    private final StatementAnalysisService statementAnalysisService;

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Analyse card statements and forecast lounge eligibility for a target month",
            description = "Upload the last 3 months of statements (CSV, TXT or PDF; up to 6 files). The API groups spend by "
                    + "calendar month, excludes cash / wallet / fuel / fees, applies the bank's rule and returns whether the "
                    + "card is eligible in targetMonth (default: next month), an explanation, and - when not eligible, or to "
                    + "stay eligible - how much qualifying spend is needed in the coming months. Nothing is stored.")
    public StatementAnalysisResponse analyze(
            @Parameter(description = "Statement files (repeat the field for multiple files)")
            @RequestParam("statements") List<MultipartFile> statements,
            @Parameter(description = "Issuing bank", example = "AXIS")
            @RequestParam("bank") Bank bank,
            @Parameter(description = "Card type", example = "CREDIT")
            @RequestParam(name = "cardType", defaultValue = "CREDIT") CardType cardType,
            @Parameter(description = "Card product, e.g. ACE, MILLENNIA, REGALIA_GOLD, INFINIA, PLATINUM_DEBIT", example = "ACE")
            @RequestParam("productCode") @NotBlank @Size(max = 40) String productCode,
            @Parameter(description = "Card issue date (yyyy-MM-dd); enables the Axis new-card exception", example = "2025-03-15")
            @RequestParam(name = "cardIssueDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate cardIssueDate,
            @Parameter(description = "Month to evaluate (yyyy-MM). Defaults to next month", example = "2026-11")
            @RequestParam(name = "targetMonth", required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth targetMonth,
            @Parameter(description = "Lounge visits already used in the calendar quarter of the target month")
            @RequestParam(name = "visitsUsedInTargetQuarter", defaultValue = "0") @Min(0) int visitsUsedInTargetQuarter,
            @Parameter(description = "Password for protected PDF statements (never stored or logged)")
            @RequestParam(name = "pdfPassword", required = false) String pdfPassword) {
        return statementAnalysisService.analyze(statements, bank, cardType, productCode, cardIssueDate, targetMonth,
                visitsUsedInTargetQuarter, pdfPassword);
    }
}
