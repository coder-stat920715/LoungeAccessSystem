package com.lounge.access.service.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lounge.access.dto.StatementCategory;
import com.lounge.access.dto.TransactionKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CsvStatementParserTest {

    private final CsvStatementParser parser = new CsvStatementParser(new TransactionCategorizer());

    @Test
    void parsesAxisStyleStatementWithPreambleAndFooter() {
        ParsedStatement statement = StatementFixtures.csv("axis_ace_eligible_aug_2026.csv");

        assertThat(statement.transactions()).hasSize(9);
        assertThat(statement.skippedLines()).isZero();
        assertThat(statement.firstDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(statement.lastDate()).isEqualTo(LocalDate.of(2026, 8, 25));

        StatementTransaction first = statement.transactions().get(0);
        assertThat(first.description()).isEqualTo("AMAZON PAY INDIA MUMBAI");
        assertThat(first.amount()).isEqualByComparingTo("6200.00");
        assertThat(first.kind()).isEqualTo(TransactionKind.PURCHASE);
        assertThat(first.category()).isEqualTo(StatementCategory.SHOPPING);
    }

    @Test
    void classifiesPaymentsAndRefundsAsCredits() {
        ParsedStatement sep = StatementFixtures.csv("axis_ace_eligible_sep_2026.csv");

        assertThat(sep.transactions()).filteredOn(t -> t.kind() == TransactionKind.PAYMENT).hasSize(1);
        assertThat(sep.transactions()).filteredOn(t -> t.kind() == TransactionKind.REFUND)
                .singleElement().satisfies(t -> assertThat(t.amount()).isEqualByComparingTo("1200.00"));
    }

    @Test
    void supportsSeparateDebitAndCreditColumns() {
        String csv = "Txn Date,Narration,Debit,Credit\n"
                + "05/10/2026,SWIGGY BANGALORE,\"1,250.00\",\n"
                + "06/10/2026,PAYMENT RECEIVED,,\"5,000.00\"\n";

        ParsedStatement statement = parser.parse("a.csv", csv);

        assertThat(statement.transactions()).hasSize(2);
        assertThat(statement.transactions().get(0).kind()).isEqualTo(TransactionKind.PURCHASE);
        assertThat(statement.transactions().get(0).amount()).isEqualByComparingTo(new BigDecimal("1250.00"));
        assertThat(statement.transactions().get(1).kind()).isEqualTo(TransactionKind.PAYMENT);
    }

    @Test
    void supportsSemicolonDelimiterAndSignedAmounts() {
        String csv = "Date;Description;Amount\n"
                + "05/10/2026;HPCL PETROL PUMP;1800.00\n"
                + "07/10/2026;CARD PAYMENT;-2000.00\n";

        ParsedStatement statement = parser.parse("a.csv", csv);

        assertThat(statement.transactions()).hasSize(2);
        assertThat(statement.transactions().get(0).category()).isEqualTo(StatementCategory.FUEL);
        assertThat(statement.transactions().get(1).kind()).isEqualTo(TransactionKind.PAYMENT);
    }

    @Test
    void countsUnreadableRowsAsSkipped() {
        String csv = "Date,Description,Amount\n"
                + "05/10/2026,SWIGGY,500.00\n"
                + "garbage,SOMETHING,100.00\n";

        ParsedStatement statement = parser.parse("a.csv", csv);

        assertThat(statement.transactions()).hasSize(1);
        assertThat(statement.skippedLines()).isEqualTo(1);
    }

    @Test
    void rejectsFilesWithoutATransactionTable() {
        assertThatThrownBy(() -> parser.parse("bad.csv", "hello,world\n1,2\n"))
                .isInstanceOf(StatementParseException.class)
                .satisfies(e -> assertThat(((StatementParseException) e).getHttpStatus()).isEqualTo(422));
    }
}
