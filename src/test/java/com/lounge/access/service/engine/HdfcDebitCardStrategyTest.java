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
import com.lounge.access.entity.Voucher;
import com.lounge.access.repository.LoungeVisitRepository;
import com.lounge.access.repository.SpendHistoryRepository;
import com.lounge.access.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HdfcDebitCardStrategyTest {

    private static final String CODE = "482915037261";

    @Mock
    private SpendHistoryRepository spendRepo;
    @Mock
    private LoungeVisitRepository visitRepo;
    @Mock
    private VoucherRepository voucherRepo;

    private HdfcDebitCardStrategy strategy;
    private Card card;

    @BeforeEach
    void setUp() {
        strategy = new HdfcDebitCardStrategy(spendRepo, visitRepo, voucherRepo, new LoungeProperties());
        card = card(Bank.HDFC, CardType.DEBIT, "PLATINUM_DEBIT", LocalDate.of(2023, 5, 1));
        when(visitRepo.countConsumedVisits(eq(1L), any(), any(), eq(NOW))).thenReturn(0L);
    }

    private Voucher voucher(Card owner, boolean used, int issuedHoursAgo, int expiresInHours) {
        return Voucher.builder().id(5L).code(CODE).card(owner).used(used)
                .issuedAt(NOW.minusHours(issuedHoursAgo)).expiresAt(NOW.plusHours(expiresInHours)).build();
    }

    private void givenVoucher(Voucher voucher) {
        when(voucherRepo.findByCode(CODE)).thenReturn(Optional.of(voucher));
    }

    private void givenSpend(String amount) {
        when(spendRepo.sumQualifyingSpend(eq(1L), eq(WINDOW_START), eq(WINDOW_END), any()))
                .thenReturn(new BigDecimal(amount));
    }

    @Test
    void supportsOnlyHdfcDebitCards() {
        assertThat(strategy.supports(card)).isTrue();
        assertThat(strategy.supports(card(Bank.HDFC, CardType.CREDIT, "INFINIA", LocalDate.of(2023, 5, 1))))
                .isFalse();
    }

    @Test
    void approvesWithValidVoucherAndQualifyingSpend() {
        Voucher voucher = voucher(card, false, 2, 22);
        givenVoucher(voucher);
        givenSpend("14500");

        EligibilityDecision decision = strategy.evaluate(ctx(card, CODE));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.voucherToConsume()).isSameAs(voucher);
        assertThat(decision.visitsRemaining()).isEqualTo(1);
    }

    @Test
    void deniesWhenSpendBelowTenThousand() {
        givenVoucher(voucher(card, false, 2, 22));
        givenSpend("6500");

        EligibilityDecision decision = strategy.evaluate(ctx(card, CODE));

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.denialReason()).isEqualTo(DenialReason.INSUFFICIENT_SPEND);
        assertThat(decision.voucherToConsume()).isNull();
    }

    @Test
    void deniesExpiredVoucher() {
        givenVoucher(Voucher.builder().id(5L).code(CODE).card(card).used(false)
                .issuedAt(NOW.minusDays(3)).expiresAt(NOW.minusDays(2)).build());
        givenSpend("14500");

        assertThat(strategy.evaluate(ctx(card, CODE)).denialReason()).isEqualTo(DenialReason.EXPIRED_VOUCHER);
    }

    @Test
    void deniesWhenVoucherMissing() {
        givenSpend("14500");

        assertThat(strategy.evaluate(ctx(card, null)).denialReason()).isEqualTo(DenialReason.INVALID_VOUCHER);
    }

    @Test
    void deniesMalformedVoucher() {
        assertThat(strategy.evaluate(ctx(card, "1234")).denialReason()).isEqualTo(DenialReason.INVALID_VOUCHER);
    }

    @Test
    void deniesUnknownVoucher() {
        when(voucherRepo.findByCode(CODE)).thenReturn(Optional.empty());

        assertThat(strategy.evaluate(ctx(card, CODE)).denialReason()).isEqualTo(DenialReason.INVALID_VOUCHER);
    }

    @Test
    void deniesAlreadyUsedVoucher() {
        givenVoucher(voucher(card, true, 2, 22));
        givenSpend("14500");

        assertThat(strategy.evaluate(ctx(card, CODE)).denialReason()).isEqualTo(DenialReason.INVALID_VOUCHER);
    }

    @Test
    void deniesVoucherIssuedForAnotherCard() {
        Card other = card(Bank.HDFC, CardType.DEBIT, "PLATINUM_DEBIT", LocalDate.of(2023, 5, 1));
        other.setId(99L);
        givenVoucher(voucher(other, false, 2, 22));
        givenSpend("14500");

        assertThat(strategy.evaluate(ctx(card, CODE)).denialReason()).isEqualTo(DenialReason.INVALID_VOUCHER);
    }

    @Test
    void deniesWhenQuarterlyQuotaExhausted() {
        givenVoucher(voucher(card, false, 2, 22));
        givenSpend("14500");
        when(visitRepo.countConsumedVisits(eq(1L), any(), any(), eq(NOW))).thenReturn(2L);

        assertThat(strategy.evaluate(ctx(card, CODE)).denialReason()).isEqualTo(DenialReason.QUOTA_EXCEEDED);
    }
}
