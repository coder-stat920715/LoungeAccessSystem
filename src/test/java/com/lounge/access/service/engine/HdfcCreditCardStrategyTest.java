package com.lounge.access.service.engine;

import static com.lounge.access.service.engine.TestFixtures.NOW;
import static com.lounge.access.service.engine.TestFixtures.WINDOW_END;
import static com.lounge.access.service.engine.TestFixtures.WINDOW_START;
import static com.lounge.access.service.engine.TestFixtures.card;
import static com.lounge.access.service.engine.TestFixtures.ctx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardType;
import com.lounge.access.dto.DenialReason;
import com.lounge.access.entity.Card;
import com.lounge.access.repository.LoungeVisitRepository;
import com.lounge.access.repository.SpendHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HdfcCreditCardStrategyTest {

    @Mock
    private SpendHistoryRepository spendRepo;
    @Mock
    private LoungeVisitRepository visitRepo;

    private HdfcCreditCardStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new HdfcCreditCardStrategy(spendRepo, visitRepo);
    }

    private Card hdfcCredit(String product) {
        return card(Bank.HDFC, CardType.CREDIT, product, LocalDate.of(2023, 1, 1));
    }

    private void givenSpend(String amount) {
        when(spendRepo.sumQualifyingSpend(eq(1L), eq(WINDOW_START), eq(WINDOW_END), any()))
                .thenReturn(new BigDecimal(amount));
    }

    private void givenConsumed(long consumed) {
        when(visitRepo.countConsumedVisits(eq(1L), any(), any(), eq(NOW))).thenReturn(consumed);
    }

    @Test
    void supportsKnownTiersOnly() {
        assertThat(strategy.supports(hdfcCredit("MILLENNIA"))).isTrue();
        assertThat(strategy.supports(hdfcCredit("Regalia Gold"))).isTrue();
        assertThat(strategy.supports(hdfcCredit("DINERS_BLACK"))).isTrue();
        assertThat(strategy.supports(hdfcCredit("FREEDOM"))).isFalse();
        assertThat(strategy.supports(card(Bank.HDFC, CardType.DEBIT, "MILLENNIA", LocalDate.of(2023, 1, 1))))
                .isFalse();
    }

    @Test
    void millenniaQualifiesAtOneLakh() {
        givenSpend("100000");
        givenConsumed(0);

        EligibilityDecision decision = strategy.evaluate(ctx(hdfcCredit("MILLENNIA"), null));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.visitsRemaining()).isZero();   // 1 visit per quarter, now used
    }

    @Test
    void millenniaDeniedJustBelowOneLakh() {
        givenSpend("99999.99");
        givenConsumed(0);

        assertThat(strategy.evaluate(ctx(hdfcCredit("MILLENNIA"), null)).denialReason())
                .isEqualTo(DenialReason.INSUFFICIENT_SPEND);
    }

    @Test
    void millenniaDeniedWhenSingleQuarterlyVisitAlreadyUsed() {
        givenSpend("120000");
        givenConsumed(1);

        assertThat(strategy.evaluate(ctx(hdfcCredit("MILLENNIA"), null)).denialReason())
                .isEqualTo(DenialReason.QUOTA_EXCEEDED);
    }

    @Test
    void regaliaGoldQualifiesAtSixtyThousandWithThreeVisits() {
        givenSpend("60000");
        givenConsumed(0);

        EligibilityDecision decision = strategy.evaluate(ctx(hdfcCredit("REGALIA_GOLD"), null));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.visitsRemaining()).isEqualTo(2);
    }

    @Test
    void regaliaGoldDeniedBelowSixtyThousand() {
        givenSpend("45000");
        givenConsumed(0);

        assertThat(strategy.evaluate(ctx(hdfcCredit("REGALIA_GOLD"), null)).denialReason())
                .isEqualTo(DenialReason.INSUFFICIENT_SPEND);
    }

    @Test
    void regaliaGoldDeniedAfterThirdVisit() {
        givenSpend("75000");
        givenConsumed(3);

        assertThat(strategy.evaluate(ctx(hdfcCredit("REGALIA_GOLD"), null)).denialReason())
                .isEqualTo(DenialReason.QUOTA_EXCEEDED);
    }

    @Test
    void infiniaIsUnlimitedWithZeroSpend() {
        EligibilityDecision decision = strategy.evaluate(ctx(hdfcCredit("INFINIA"), null));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.visitsRemaining()).isEqualTo(EligibilityDecision.UNLIMITED);
        verifyNoInteractions(spendRepo, visitRepo);
    }

    @Test
    void dinersBlackIsUnlimitedWithZeroSpend() {
        EligibilityDecision decision = strategy.evaluate(ctx(hdfcCredit("Diners Black"), null));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.visitsRemaining()).isEqualTo(EligibilityDecision.UNLIMITED);
    }
}
