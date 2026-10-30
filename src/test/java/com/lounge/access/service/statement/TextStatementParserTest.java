package com.lounge.access.service.statement;

import static org.assertj.core.api.Assertions.assertThat;

import com.lounge.access.dto.StatementCategory;
import com.lounge.access.dto.TransactionKind;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TextStatementParserTest {

    private final TextStatementParser parser = new TextStatementParser(new TransactionCategorizer());

    @Test
    void parsesTransactionLinesAndIgnoresNoise() {
        ParsedStatement statement = StatementFixtures.csv("axis_ace_eligible_aug_2026.csv"); // sanity: CSV is fine too
        assertThat(statement.transactions()).isNotEmpty();

        ParsedStatement text = parser.parse("stmt.txt", "TEXT",
                StatementFixtures.read("axis_ace_eligible_aug_oct_2026_statement.txt"));

        assertThat(text.transactions()).hasSize(30);
        assertThat(text.skippedLines()).isZero();
        assertThat(text.firstDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(text.lastDate()).isEqualTo(LocalDate.of(2026, 10, 29));
    }

    @Test
    void readsDrCrSuffixAndCategorises() {
        ParsedStatement text = parser.parse("s.txt", "TEXT",
                "03/08/2026 AMAZON PAY INDIA MUMBAI 6,200.00 Dr\n"
                        + "25/08/2026 PAYMENT RECEIVED - THANK YOU 18,000.00 Cr\n"
                        + "24/09/2026 REFUND AMAZON PAY INDIA 1,200.00 Cr\n");

        assertThat(text.transactions()).extracting(StatementTransaction::kind)
                .containsExactly(TransactionKind.PURCHASE, TransactionKind.PAYMENT, TransactionKind.REFUND);
        assertThat(text.transactions().get(0).category()).isEqualTo(StatementCategory.SHOPPING);
    }

    @Test
    void handlesTextualMonthDatesAndRupeeSymbol() {
        ParsedStatement text = parser.parse("s.txt", "TEXT", "12 Aug 2026 HPCL PETROL PUMP \u20B9 2,000.00 Dr\n");

        assertThat(text.transactions()).hasSize(1);
        assertThat(text.transactions().get(0).date()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(text.transactions().get(0).category()).isEqualTo(StatementCategory.FUEL);
    }

    @Test
    void countsDateLinesThatDoNotFitThePattern() {
        ParsedStatement text = parser.parse("s.txt", "PDF",
                "03/08/2026 AMAZON PAY INDIA 6,200.00 Dr\n03/08/2026 BROKEN LINE WITHOUT AMOUNT\nPage 1 of 2\n");

        assertThat(text.transactions()).hasSize(1);
        assertThat(text.skippedLines()).isEqualTo(1);
    }
}
