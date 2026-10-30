package com.lounge.access.service.engine;

import com.lounge.access.config.LoungeProperties;
import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardType;
import com.lounge.access.dto.DenialReason;
import com.lounge.access.entity.Card;
import com.lounge.access.entity.Voucher;
import com.lounge.access.repository.LoungeVisitRepository;
import com.lounge.access.repository.SpendHistoryRepository;
import com.lounge.access.repository.VoucherRepository;
import com.lounge.access.util.DateRangeUtil;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * HDFC debit cards: minimum spend in the previous calendar quarter AND a valid single-use 12-digit digital voucher
 * generated before entry. The voucher is only consumed when the visit is approved.
 */
@Component
public class HdfcDebitCardStrategy extends AbstractSpendLinkedStrategy {

    private static final String VOUCHER_FORMAT = "\\d{12}";

    private final VoucherRepository voucherRepository;
    private final LoungeProperties.HdfcDebit config;

    public HdfcDebitCardStrategy(SpendHistoryRepository spendHistoryRepository,
                                 LoungeVisitRepository loungeVisitRepository,
                                 VoucherRepository voucherRepository,
                                 LoungeProperties properties) {
        super(spendHistoryRepository, loungeVisitRepository);
        this.voucherRepository = voucherRepository;
        this.config = properties.getHdfcDebit();
    }

    @Override
    public String name() {
        return "HDFC_DEBIT";
    }

    @Override
    public boolean supports(Card card) {
        return card.getBank() == Bank.HDFC && card.getCardType() == CardType.DEBIT;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public EligibilityDecision evaluate(EligibilityContext ctx) {
        Card card = ctx.card();
        String code = ctx.voucherCode() == null ? "" : ctx.voucherCode().trim();

        if (!code.matches(VOUCHER_FORMAT)) {
            return EligibilityDecision.deny(DenialReason.INVALID_VOUCHER,
                    "A valid 12-digit digital voucher code is mandatory for HDFC debit cards", 0);
        }
        Optional<Voucher> found = voucherRepository.findByCode(code);
        if (found.isEmpty()) {
            return EligibilityDecision.deny(DenialReason.INVALID_VOUCHER, "Voucher code not recognised", 0);
        }
        Voucher voucher = found.get();
        if (voucher.getCard() == null || !Objects.equals(voucher.getCard().getId(), card.getId())) {
            return EligibilityDecision.deny(DenialReason.INVALID_VOUCHER,
                    "Voucher was not issued for this card", 0);
        }
        if (voucher.isUsed()) {
            return EligibilityDecision.deny(DenialReason.INVALID_VOUCHER, "Voucher has already been used", 0);
        }
        if (voucher.getIssuedAt().isAfter(ctx.now())) {
            return EligibilityDecision.deny(DenialReason.INVALID_VOUCHER,
                    "Voucher is not yet active; it must be generated prior to entry", 0);
        }
        if (!voucher.getExpiresAt().isAfter(ctx.now())) {
            return EligibilityDecision.deny(DenialReason.EXPIRED_VOUCHER,
                    "Voucher expired at " + voucher.getExpiresAt(), 0);
        }

        SpendPolicy policy = new SpendPolicy(
                config.getMinimumSpend(),
                DateRangeUtil.previousQuarter(ctx.today()),
                false,
                null,
                config.getVisitsPerQuarter());
        return applyPolicy(ctx, policy, voucher);
    }
}
