package com.lounge.access.service.statement;

import static org.assertj.core.api.Assertions.assertThat;

import com.lounge.access.dto.StatementCategory;
import org.junit.jupiter.api.Test;

class TransactionCategorizerTest {

    private final TransactionCategorizer categorizer = new TransactionCategorizer();

    @Test
    void excludedCategoriesAreRecognisedBeforeMerchantCategories() {
        assertThat(categorizer.categorize("PAYTM ADD MONEY NOIDA", "WALLET")).isEqualTo(StatementCategory.WALLET_RELOAD);
        assertThat(categorizer.categorize("HPCL PETROL PUMP DELHI", null)).isEqualTo(StatementCategory.FUEL);
        assertThat(categorizer.categorize("ATM WDL AXIS BANK", "ATM")).isEqualTo(StatementCategory.CASH_WITHDRAWAL);
        assertThat(categorizer.categorize("ANNUAL FEE ACE CARD", "FEES & CHARGES")).isEqualTo(StatementCategory.FEES_AND_CHARGES);
        assertThat(categorizer.categorize("IGST @18%", null)).isEqualTo(StatementCategory.FEES_AND_CHARGES);
    }

    @Test
    void ordinaryMerchantsAreCategorised() {
        assertThat(categorizer.categorize("AMAZON PAY INDIA MUMBAI", "E-COMMERCE")).isEqualTo(StatementCategory.SHOPPING);
        assertThat(categorizer.categorize("SWIGGY BANGALORE", "RESTAURANTS")).isEqualTo(StatementCategory.DINING);
        assertThat(categorizer.categorize("BIGBASKET", null)).isEqualTo(StatementCategory.GROCERY);
        assertThat(categorizer.categorize("INDIGO AIRLINES", "TRAVEL")).isEqualTo(StatementCategory.TRAVEL);
        assertThat(categorizer.categorize("BESCOM ELECTRICITY BILL", null)).isEqualTo(StatementCategory.UTILITIES);
        assertThat(categorizer.categorize("BOOKMYSHOW", null)).isEqualTo(StatementCategory.ENTERTAINMENT);
        assertThat(categorizer.categorize("APOLLO PHARMACY", "MEDICAL")).isEqualTo(StatementCategory.HEALTH);
    }

    @Test
    void unknownMerchantsAreOtherAndQualifying() {
        StatementCategory category = categorizer.categorize("XYZ TRADERS", null);

        assertThat(category).isEqualTo(StatementCategory.OTHER);
        assertThat(category.isQualifying()).isTrue();
    }

    @Test
    void onlyCashWalletFuelAndFeesAreNonQualifying() {
        for (StatementCategory c : StatementCategory.values()) {
            boolean expectedNonQualifying = c == StatementCategory.CASH_WITHDRAWAL || c == StatementCategory.WALLET_RELOAD
                    || c == StatementCategory.FUEL || c == StatementCategory.FEES_AND_CHARGES;
            assertThat(c.isQualifying()).as(c.name()).isEqualTo(!expectedNonQualifying);
        }
    }
}
