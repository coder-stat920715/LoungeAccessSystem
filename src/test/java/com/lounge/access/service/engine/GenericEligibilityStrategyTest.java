package com.lounge.access.service.engine;

import static com.lounge.access.service.engine.TestFixtures.NOW;
import static com.lounge.access.service.engine.TestFixtures.WINDOW_END;
import static com.lounge.access.service.engine.TestFixtures.WINDOW_START;
import static com.lounge.access.service.engine.TestFixtures.card;
import static com.lounge.access.service.engine.TestFixtures.ctx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.lounge.access.config.LoungeProperties;
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
class GenericEligibilityStrategyTest {

    @Mock
    private SpendHistoryRepository spendRepo;
    @Mock
    private LoungeVisitRepository visitRepo;

    private GenericEligibilityStrategy strategy;
    private final Card icici = card(Bank.ICICI, CardType.CREDIT, "CORAL", LocalDate.of(2024, 3, 1));

    @BeforeEach
    void setUp() {
        strategy = new GenericEligibilityStrategy(spendRepo, visitRepo, new LoungeProperties());
        when(visitRepo.countConsumedVisits(eq(1L), any(), any(), eq(NOW))).thenReturn(0L);
    }

    private void givenSpend(String amount) {
        when(spendRepo.sumQualifyingSpend(eq(1L), eq(WINDOW_START), eq(WINDOW_END), any()))
                .thenReturn(new BigDecimal(amount));
    }

    @Test
    void supportsConfiguredBanksOnly() {
        assertThat(strategy.supports(icici)).isTrue();
        assertThat(strategy.supports(card(Bank.KOTAK, CardType.DEBIT, "811", LocalDate.of(2024, 3, 1)))).isFalse();
    }

    @Test
    void approvesQualifyingSpend() {
        givenSpend("30000");

        EligibilityDecision decision = strategy.evaluate(ctx(icici, null));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.visitsRemaining()).isEqualTo(1);
    }

    @Test
    void deniesNonQualifyingSpend() {
        givenSpend("24999");

        assertThat(strategy.evaluate(ctx(icici, null)).denialReason()).isEqualTo(DenialReason.INSUFFICIENT_SPEND);
    }

    @Test
    void deniesWhenQuotaExhausted() {
        givenSpend("30000");
        when(visitRepo.countConsumedVisits(eq(1L), any(), any(), eq(NOW))).thenReturn(2L);

        assertThat(strategy.evaluate(ctx(icici, null)).denialReason()).isEqualTo(DenialReason.QUOTA_EXCEEDED);
    }
}
