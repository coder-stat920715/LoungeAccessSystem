package com.lounge.access.service.engine;

import static com.lounge.access.service.engine.TestFixtures.NOW;
import static com.lounge.access.service.engine.TestFixtures.WINDOW_END;
import static com.lounge.access.service.engine.TestFixtures.WINDOW_START;
import static com.lounge.access.service.engine.TestFixtures.card;
import static com.lounge.access.service.engine.TestFixtures.ctx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lounge.access.config.LoungeProperties;
import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardType;
import com.lounge.access.dto.DenialReason;
import com.lounge.access.dto.SpendCategory;
import com.lounge.access.entity.Card;
import com.lounge.access.repository.LoungeVisitRepository;
import com.lounge.access.repository.SpendHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AxisBankStrategyTest {

    @Mock
    private SpendHistoryRepository spendRepo;
    @Mock
    private LoungeVisitRepository visitRepo;
    @Captor
    private ArgumentCaptor<Collection<SpendCategory>> excludedCaptor;

    private AxisBankStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new AxisBankStrategy(spendRepo, visitRepo, new LoungeProperties());
    }

    private void givenSpend(String amount) {
        when(spendRepo.sumQualifyingSpend(eq(1L), eq(WINDOW_START), eq(WINDOW_END), any()))
                .thenReturn(amount == null ? null : new BigDecimal(amount));
    }

    private void givenConsumedVisits(long consumed) {
        when(visitRepo.countConsumedVisits(eq(1L), any(), any(), eq(NOW))).thenReturn(consumed);
    }

    private Card oldCard() {
        return card(Bank.AXIS, CardType.CREDIT, "ACE", LocalDate.of(2024, 1, 15));
    }

    @Test
    void supportsOnlyAxisCards() {
        assertThat(strategy.supports(oldCard())).isTrue();
        assertThat(strategy.supports(card(Bank.HDFC, CardType.CREDIT, "ACE", LocalDate.of(2024, 1, 1)))).isFalse();
    }

    @Test
    void approvesWhenSpendMeetsThresholdAndDecrementsQuota() {
        givenSpend("62000");
        givenConsumedVisits(0);

        EligibilityDecision decision = strategy.evaluate(ctx(oldCard(), null));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.visitsRemaining()).isEqualTo(1);   // 2 per quarter, this entry uses one
        assertThat(decision.denialReason()).isNull();
    }

    @Test
    void approvesAtExactThreshold() {
        givenSpend("50000");
        givenConsumedVisits(0);

        assertThat(strategy.evaluate(ctx(oldCard(), null)).eligible()).isTrue();
    }

    @Test
    void deniesWhenSpendBelowThreshold() {
        givenSpend("20000");
        givenConsumedVisits(0);

        EligibilityDecision decision = strategy.evaluate(ctx(oldCard(), null));

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.denialReason()).isEqualTo(DenialReason.INSUFFICIENT_SPEND);
    }

    @Test
    void deniesWhenNoSpendHistoryExists() {
        givenSpend(null);
        givenConsumedVisits(0);

        assertThat(strategy.evaluate(ctx(oldCard(), null)).denialReason())
                .isEqualTo(DenialReason.INSUFFICIENT_SPEND);
    }

    @Test
    void excludesCashWalletAndFuelFromQualifyingSpend() {
        givenSpend("60000");
        givenConsumedVisits(0);

        strategy.evaluate(ctx(oldCard(), null));

        verify(spendRepo).sumQualifyingSpend(eq(1L), eq(WINDOW_START), eq(WINDOW_END), excludedCaptor.capture());
        assertThat(excludedCaptor.getValue()).containsExactlyInAnyOrder(
                SpendCategory.CASH_WITHDRAWAL, SpendCategory.WALLET_RELOAD, SpendCategory.FUEL);
    }

    @Test
    void newCardExceptionWaivesSpendRequirementInIssueMonth() {
        givenSpend("0");
        givenConsumedVisits(0);
        Card newCard = card(Bank.AXIS, CardType.CREDIT, "ACE", LocalDate.of(2026, 10, 2));

        assertThat(strategy.evaluate(ctx(newCard, null)).eligible()).isTrue();
    }

    @Test
    void newCardExceptionStillAppliesThroughIssueMonthPlusThree() {
        givenSpend("0");
        givenConsumedVisits(0);
        Card issuedJuly = card(Bank.AXIS, CardType.CREDIT, "ACE", LocalDate.of(2026, 7, 10)); // waived to 31 Oct

        assertThat(strategy.evaluate(ctx(issuedJuly, null)).eligible()).isTrue();
    }

    @Test
    void newCardExceptionExpiresAfterIssueMonthPlusThree() {
        givenSpend("0");
        givenConsumedVisits(0);
        Card issuedJune = card(Bank.AXIS, CardType.CREDIT, "ACE", LocalDate.of(2026, 6, 15)); // waived to 30 Sep

        assertThat(strategy.evaluate(ctx(issuedJune, null)).denialReason())
                .isEqualTo(DenialReason.INSUFFICIENT_SPEND);
    }

    @Test
    void deniesWhenQuarterlyQuotaIsExhausted() {
        givenSpend("62000");
        givenConsumedVisits(2);

        EligibilityDecision decision = strategy.evaluate(ctx(oldCard(), null));

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.denialReason()).isEqualTo(DenialReason.QUOTA_EXCEEDED);
        assertThat(decision.visitsRemaining()).isZero();
    }
}
