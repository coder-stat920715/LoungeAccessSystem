package com.lounge.access.service.statement;

import static org.assertj.core.api.Assertions.assertThat;

import com.lounge.access.config.LoungeProperties;
import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardType;
import com.lounge.access.service.statement.SpendRule.WindowKind;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class SpendRuleResolverTest {

    private final SpendRuleResolver resolver = new SpendRuleResolver(new LoungeProperties());

    @Test
    void axisUsesRollingThreeMonthWindow() {
        SpendRule rule = resolver.resolve(Bank.AXIS, CardType.CREDIT, "ACE").orElseThrow();

        assertThat(rule.minimumSpend()).isEqualByComparingTo("50000");
        assertThat(rule.windowKind()).isEqualTo(WindowKind.ROLLING_MONTHS);
        assertThat(rule.windowFor(YearMonth.of(2026, 11)).start()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(rule.windowFor(YearMonth.of(2026, 11)).end()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(rule.windowMonths(YearMonth.of(2026, 12)))
                .containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11));
    }

    @Test
    void hdfcCreditUsesPreviousQuarterAndTierThresholds() {
        SpendRule millennia = resolver.resolve(Bank.HDFC, CardType.CREDIT, "MILLENNIA").orElseThrow();
        SpendRule regalia = resolver.resolve(Bank.HDFC, CardType.CREDIT, "Regalia Gold").orElseThrow();
        SpendRule infinia = resolver.resolve(Bank.HDFC, CardType.CREDIT, "INFINIA").orElseThrow();

        assertThat(millennia.minimumSpend()).isEqualByComparingTo("100000");
        assertThat(millennia.visitsPerQuarter()).isEqualTo(1);
        assertThat(regalia.minimumSpend()).isEqualByComparingTo("60000");
        assertThat(regalia.visitsPerQuarter()).isEqualTo(3);
        assertThat(infinia.minimumSpend().signum()).isZero();
        assertThat(millennia.windowFor(YearMonth.of(2026, 11)).start()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(millennia.windowFor(YearMonth.of(2026, 12)).end()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(millennia.windowFor(YearMonth.of(2027, 1)).start()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void hdfcDebitRequiresVoucher() {
        SpendRule rule = resolver.resolve(Bank.HDFC, CardType.DEBIT, "PLATINUM_DEBIT").orElseThrow();

        assertThat(rule.voucherRequired()).isTrue();
        assertThat(rule.minimumSpend()).isEqualByComparingTo("10000");
    }

    @Test
    void genericBanksAreSupportedAndOthersAreNot() {
        assertThat(resolver.resolve(Bank.ICICI, CardType.CREDIT, "CORAL")).isPresent();
        assertThat(resolver.resolve(Bank.KOTAK, CardType.DEBIT, "811")).isEmpty();
        assertThat(resolver.resolve(Bank.HDFC, CardType.CREDIT, "FREEDOM")).isEmpty();
    }
}
