package com.lounge.access.service.statement;

import static org.assertj.core.api.Assertions.assertThat;

import com.lounge.access.config.LoungeProperties;
import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardType;
import com.lounge.access.dto.EligibilityStatus;
import com.lounge.access.dto.StatementAnalysisResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatementAnalyzerTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    private final StatementAnalyzer analyzer = new StatementAnalyzer(new SpendRuleResolver(new LoungeProperties()));

    private StatementAnalysisResponse axis(List<ParsedStatement> statements, LocalDate issueDate, int visitsUsed) {
        return analyzer.analyze(new AnalysisRequest(Bank.AXIS, CardType.CREDIT, "ACE", issueDate, NOVEMBER, visitsUsed),
                statements);
    }

    @Test
    void qualifyingSpendAboveThresholdMakesCardEligibleForNovember() {
        StatementAnalysisResponse r = axis(StatementFixtures.axisStatements("eligible", "aug", "sep", "oct"), null, 0);

        assertThat(r.status()).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(r.eligibleForTargetMonth()).isTrue();
        assertThat(r.qualifyingSpendInWindow()).isEqualByComparingTo("66500");
        assertThat(r.requiredSpend()).isEqualByComparingTo("50000");
        assertThat(r.surplusOrShortfall()).isEqualByComparingTo("16500");
        assertThat(r.progressPercent()).isEqualTo(100.0);
        assertThat(r.dataComplete()).isTrue();
        assertThat(r.window().start()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(r.window().end()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(r.quota().visitsAvailable()).isEqualTo(2);
    }

    @Test
    void eligibleCardStillGetsAPlanToStayEligibleInDecember() {
        StatementAnalysisResponse r = axis(StatementFixtures.axisStatements("eligible", "aug", "sep", "oct"), null, 0);

        assertThat(r.planForUpcomingMonths().planMonth()).isEqualTo("2026-12");
        assertThat(r.planForUpcomingMonths().planMonthIsFollowingMonth()).isTrue();
        assertThat(r.planForUpcomingMonths().spendAlreadyBanked()).isEqualByComparingTo("45000");      // Sep + Oct
        assertThat(r.planForUpcomingMonths().additionalQualifyingSpendNeeded()).isEqualByComparingTo("5000");
        assertThat(r.planForUpcomingMonths().monthsLeavingWindow()).containsExactly("2026-08");
        assertThat(r.planForUpcomingMonths().spendLeavingWindow()).isEqualByComparingTo("21500");
        assertThat(r.planForUpcomingMonths().monthsStillToCome()).containsExactly("2026-11");
        assertThat(r.recommendations()).anyMatch(s -> s.contains("stay eligible in December 2026"));
    }

    @Test
    void nonQualifyingCategoriesAreExcludedAndReported() {
        StatementAnalysisResponse r = axis(StatementFixtures.axisStatements("eligible", "aug", "sep", "oct"), null, 0);

        assertThat(r.excludedSpend().total()).isEqualByComparingTo("17590");
        assertThat(r.excludedSpend().byCategory()).containsEntry("FUEL", new java.math.BigDecimal("6000.00"))
                .containsEntry("CASH_WITHDRAWAL", new java.math.BigDecimal("8000.00"))
                .containsEntry("WALLET_RELOAD", new java.math.BigDecimal("3000.00"))
                .containsEntry("FEES_AND_CHARGES", new java.math.BigDecimal("590.00"));
        assertThat(r.categoryBreakdown()).filteredOn(c -> !c.qualifying()).hasSize(4);
        assertThat(r.monthlyBreakdown()).extracting(m -> m.qualifyingSpend().intValue())
                .containsExactly(21500, 26300, 18700);
        assertThat(r.topMerchants()).hasSize(5);
        assertThat(r.warnings()).anyMatch(w -> w.contains("Refunds"));
    }

    @Test
    void belowThresholdIsNotEligibleAndExplainsWhyAndWhatToDo() {
        StatementAnalysisResponse r = axis(StatementFixtures.axisStatements("not_eligible", "aug", "sep", "oct"), null, 0);

        assertThat(r.status()).isEqualTo(EligibilityStatus.NOT_ELIGIBLE);
        assertThat(r.eligibleForTargetMonth()).isFalse();
        assertThat(r.qualifyingSpendInWindow()).isEqualByComparingTo("30800");
        assertThat(r.surplusOrShortfall()).isEqualByComparingTo("-19200");
        assertThat(r.progressPercent()).isEqualTo(61.6);
        assertThat(r.excludedSpend().total()).isEqualByComparingTo("36000");
        assertThat(r.excludedSpend().percentOfAllSpend()).isEqualTo(53.9);

        assertThat(r.planForUpcomingMonths().planMonth()).isEqualTo("2026-12");
        assertThat(r.planForUpcomingMonths().spendAlreadyBanked()).isEqualByComparingTo("21000");
        assertThat(r.planForUpcomingMonths().additionalQualifyingSpendNeeded()).isEqualByComparingTo("29000");
        assertThat(r.planForUpcomingMonths().perMonthTarget()).isEqualByComparingTo("29000");
        assertThat(r.planForUpcomingMonths().alreadySecured()).isFalse();

        assertThat(r.summary()).contains("NOT eligible");
        assertThat(r.recommendations()).anyMatch(s -> s.startsWith("Step 1") && s.contains("December 2026"));
        assertThat(r.recommendations()).anyMatch(s -> s.contains("fuel") && s.contains("wallet reload"));
    }

    @Test
    void newCardExceptionWaivesSpendRequirement() {
        StatementAnalysisResponse r = axis(StatementFixtures.axisStatements("not_eligible", "aug", "sep", "oct"),
                LocalDate.of(2026, 9, 10), 0);

        assertThat(r.status()).isEqualTo(EligibilityStatus.ELIGIBLE_NEW_CARD_EXCEPTION);
        assertThat(r.eligibleForTargetMonth()).isTrue();
        assertThat(r.recommendations().get(0)).contains("valid until 2026-12-31");
    }

    @Test
    void newCardExceptionExpires() {
        StatementAnalysisResponse r = axis(StatementFixtures.axisStatements("not_eligible", "aug", "sep", "oct"),
                LocalDate.of(2026, 6, 10), 0);   // waived only until 2026-09-30

        assertThat(r.status()).isEqualTo(EligibilityStatus.NOT_ELIGIBLE);
    }

    @Test
    void exhaustedQuotaBlocksEligibilityEvenWhenSpendQualifies() {
        StatementAnalysisResponse r = axis(StatementFixtures.axisStatements("eligible", "aug", "sep", "oct"), null, 2);

        assertThat(r.status()).isEqualTo(EligibilityStatus.QUOTA_EXHAUSTED);
        assertThat(r.eligibleForTargetMonth()).isFalse();
        assertThat(r.quota().visitsAvailable()).isZero();
    }

    @Test
    void missingMonthIsFlaggedAsIncompleteData() {
        StatementAnalysisResponse r = axis(StatementFixtures.axisStatements("eligible", "aug", "sep"), null, 0);

        assertThat(r.status()).isEqualTo(EligibilityStatus.NOT_ELIGIBLE);
        assertThat(r.qualifyingSpendInWindow()).isEqualByComparingTo("47800");
        assertThat(r.dataComplete()).isFalse();
        assertThat(r.warnings()).anyMatch(w -> w.contains("No transactions found for October 2026"));
        assertThat(r.recommendations()).anyMatch(s -> s.startsWith("Data note"));
    }

    @Test
    void textStatementGivesTheSameResultAsCsv() {
        ParsedStatement text = new TextStatementParser(new TransactionCategorizer()).parse("s.txt", "TEXT",
                StatementFixtures.read("axis_ace_eligible_aug_oct_2026_statement.txt"));

        StatementAnalysisResponse r = axis(List.of(text), null, 0);

        assertThat(r.status()).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(r.qualifyingSpendInWindow()).isEqualByComparingTo("66500");
    }

    @Test
    void duplicatesAcrossOverlappingFilesAreCountedOnce() {
        List<ParsedStatement> files = new ArrayList<>(StatementFixtures.axisStatements("eligible", "aug", "sep", "oct"));
        files.add(StatementFixtures.csv("axis_ace_eligible_aug_2026.csv"));   // same month uploaded twice

        StatementAnalysisResponse r = axis(files, null, 0);

        assertThat(r.qualifyingSpendInWindow()).isEqualByComparingTo("66500");
        assertThat(r.warnings()).anyMatch(w -> w.contains("counted once"));
    }

    @Test
    void hdfcQuarterRuleLooksAtPreviousQuarterAndPlansForJanuary() {
        StatementAnalysisResponse r = analyzer.analyze(
                new AnalysisRequest(Bank.HDFC, CardType.CREDIT, "MILLENNIA", null, NOVEMBER, 0),
                StatementFixtures.axisStatements("eligible", "aug", "sep", "oct"));

        assertThat(r.status()).isEqualTo(EligibilityStatus.NOT_ELIGIBLE);
        assertThat(r.window().start()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(r.window().end()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(r.qualifyingSpendInWindow()).isEqualByComparingTo("47800");
        assertThat(r.dataComplete()).isFalse();                                   // July missing
        assertThat(r.planForUpcomingMonths().planMonth()).isEqualTo("2027-01");
        assertThat(r.planForUpcomingMonths().planMonthIsFollowingMonth()).isFalse();
        assertThat(r.planForUpcomingMonths().spendAlreadyBanked()).isEqualByComparingTo("18700");   // October
        assertThat(r.planForUpcomingMonths().additionalQualifyingSpendNeeded()).isEqualByComparingTo("81300");
        assertThat(r.planForUpcomingMonths().perMonthTarget()).isEqualByComparingTo("40650");       // Nov + Dec
        assertThat(r.planForUpcomingMonths().followingMonthOutlook()).contains("already closed");
    }

    @Test
    void infiniaNeedsNoSpend() {
        StatementAnalysisResponse r = analyzer.analyze(
                new AnalysisRequest(Bank.HDFC, CardType.CREDIT, "INFINIA", null, NOVEMBER, 0),
                StatementFixtures.axisStatements("not_eligible", "aug"));

        assertThat(r.status()).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(r.quota().visitsAvailable()).isEqualTo(-1);
        assertThat(r.dataComplete()).isTrue();
    }

    @Test
    void unsupportedProgrammeIsReported() {
        StatementAnalysisResponse r = analyzer.analyze(
                new AnalysisRequest(Bank.KOTAK, CardType.DEBIT, "811", null, NOVEMBER, 0),
                StatementFixtures.axisStatements("eligible", "aug"));

        assertThat(r.status()).isEqualTo(EligibilityStatus.NOT_SUPPORTED);
        assertThat(r.eligibleForTargetMonth()).isFalse();
    }
}
