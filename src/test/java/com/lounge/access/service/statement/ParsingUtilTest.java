package com.lounge.access.service.statement;

import static org.assertj.core.api.Assertions.assertThat;

import com.lounge.access.service.statement.ParsingUtil.ParsedAmount;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ParsingUtilTest {

    @Test
    void parsesCommonIndianDateFormats() {
        LocalDate expected = LocalDate.of(2026, 8, 3);
        assertThat(ParsingUtil.parseDate("03/08/2026")).isEqualTo(expected);
        assertThat(ParsingUtil.parseDate("3-8-2026")).isEqualTo(expected);
        assertThat(ParsingUtil.parseDate("03.08.2026")).isEqualTo(expected);
        assertThat(ParsingUtil.parseDate("2026-08-03")).isEqualTo(expected);
        assertThat(ParsingUtil.parseDate("03/08/26")).isEqualTo(expected);
        assertThat(ParsingUtil.parseDate("03 Aug 2026")).isEqualTo(expected);
        assertThat(ParsingUtil.parseDate("03-AUG-2026")).isEqualTo(expected);
        assertThat(ParsingUtil.parseDate("03/08/2026 14:35:10")).isEqualTo(expected);
    }

    @Test
    void datesAreDayFirstAndGarbageIsRejected() {
        assertThat(ParsingUtil.parseDate("02/03/2026")).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(ParsingUtil.parseDate("not a date")).isNull();
        assertThat(ParsingUtil.parseDate("")).isNull();
        assertThat(ParsingUtil.parseDate(null)).isNull();
    }

    @Test
    void parsesAmountsWithGroupingCurrencyAndDrCr() {
        ParsedAmount lakh = ParsingUtil.parseAmount("1,23,456.50");
        assertThat(lakh.value()).isEqualByComparingTo(new BigDecimal("123456.50"));
        assertThat(lakh.debit()).isNull();

        assertThat(ParsingUtil.parseAmount("Rs. 450").value()).isEqualByComparingTo("450");
        assertThat(ParsingUtil.parseAmount("\u20B9 1,200.00 Dr").debit()).isTrue();
        assertThat(ParsingUtil.parseAmount("1,200.00 Cr").debit()).isFalse();
        assertThat(ParsingUtil.parseAmount("(450.00)").debit()).isFalse();
        assertThat(ParsingUtil.parseAmount("-450.00").debit()).isFalse();
        assertThat(ParsingUtil.parseAmount("INR 99.5").value()).isEqualByComparingTo("99.5");
    }

    @Test
    void nonNumericAmountsAreRejected() {
        assertThat(ParsingUtil.parseAmount("N/A")).isNull();
        assertThat(ParsingUtil.parseAmount("  ")).isNull();
        assertThat(ParsingUtil.parseAmount(null)).isNull();
    }
}
