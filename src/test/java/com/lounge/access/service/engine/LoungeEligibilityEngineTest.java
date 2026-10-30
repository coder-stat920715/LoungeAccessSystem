package com.lounge.access.service.engine;

import static com.lounge.access.service.engine.TestFixtures.NOW;
import static com.lounge.access.service.engine.TestFixtures.card;
import static com.lounge.access.service.engine.TestFixtures.ctx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardType;
import com.lounge.access.dto.DenialReason;
import com.lounge.access.entity.Card;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoungeEligibilityEngineTest {

    private final Card axis = card(Bank.AXIS, CardType.CREDIT, "ACE", LocalDate.of(2024, 1, 1));

    private EligibilityStrategy strategy(boolean supports, int priority, EligibilityDecision result) {
        EligibilityStrategy s = mock(EligibilityStrategy.class);
        when(s.supports(any())).thenReturn(supports);
        when(s.priority()).thenReturn(priority);
        when(s.name()).thenReturn("MOCK");
        when(s.evaluate(any())).thenReturn(result);
        return s;
    }

    @Test
    void delegatesToHighestPrioritySupportingStrategy() {
        EligibilityDecision specific = EligibilityDecision.approve("specific", 1, null);
        EligibilityDecision fallback = EligibilityDecision.approve("fallback", 1, null);
        EligibilityStrategy generic = strategy(true, 1000, fallback);
        EligibilityStrategy bank = strategy(true, 10, specific);

        LoungeEligibilityEngine engine = new LoungeEligibilityEngine(List.of(generic, bank));

        assertThat(engine.evaluate(ctx(axis, null))).isSameAs(specific);
        verify(generic, never()).evaluate(any());
    }

    @Test
    void skipsStrategiesThatDoNotSupportTheCard() {
        EligibilityDecision fallback = EligibilityDecision.approve("fallback", 1, null);
        EligibilityStrategy other = strategy(false, 10, EligibilityDecision.approve("other", 1, null));
        EligibilityStrategy generic = strategy(true, 1000, fallback);

        assertThat(new LoungeEligibilityEngine(List.of(other, generic)).evaluate(ctx(axis, null)))
                .isSameAs(fallback);
    }

    @Test
    void returnsUnsupportedCardWhenNoStrategyMatches() {
        EligibilityStrategy other = strategy(false, 10, EligibilityDecision.approve("other", 1, null));

        EligibilityDecision decision = new LoungeEligibilityEngine(List.of(other)).evaluate(ctx(axis, null));

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.denialReason()).isEqualTo(DenialReason.UNSUPPORTED_CARD);
    }

    @Test
    void deniesBlockedCardsWithoutConsultingStrategies() {
        EligibilityStrategy s = strategy(true, 10, EligibilityDecision.approve("x", 1, null));
        axis.setActive(false);

        EligibilityDecision decision = new LoungeEligibilityEngine(List.of(s)).evaluate(ctx(axis, null));

        assertThat(decision.denialReason()).isEqualTo(DenialReason.CARD_INACTIVE);
        verify(s, never()).evaluate(any());
    }

    @Test
    void deniesExpiredCards() {
        EligibilityStrategy s = strategy(true, 10, EligibilityDecision.approve("x", 1, null));
        axis.setExpiryDate(NOW.toLocalDate().minusDays(1));

        assertThat(new LoungeEligibilityEngine(List.of(s)).evaluate(ctx(axis, null)).denialReason())
                .isEqualTo(DenialReason.CARD_INACTIVE);
    }
}
