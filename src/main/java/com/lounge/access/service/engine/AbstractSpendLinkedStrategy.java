package com.lounge.access.service.engine;

import static com.lounge.access.service.engine.EligibilityDecision.UNLIMITED;

import com.lounge.access.dto.DenialReason;
import com.lounge.access.dto.SpendCategory;
import com.lounge.access.entity.Card;
import com.lounge.access.entity.Voucher;
import com.lounge.access.repository.LoungeVisitRepository;
import com.lounge.access.repository.SpendHistoryRepository;
import com.lounge.access.util.DateRangeUtil;
import com.lounge.access.util.DateRangeUtil.DateRange;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** Shared spend-threshold + quarterly-quota logic; concrete strategies only supply the policy numbers. */
public abstract class AbstractSpendLinkedStrategy implements EligibilityStrategy {

    protected final SpendHistoryRepository spendHistoryRepository;
    protected final LoungeVisitRepository loungeVisitRepository;

    protected AbstractSpendLinkedStrategy(SpendHistoryRepository spendHistoryRepository,
                                          LoungeVisitRepository loungeVisitRepository) {
        this.spendHistoryRepository = spendHistoryRepository;
        this.loungeVisitRepository = loungeVisitRepository;
    }

    /**
     * @param spendWaived whether the spend requirement is waived (e.g. Axis new-card exception)
     * @param visitsPerQuarter quota per calendar quarter, or {@link EligibilityDecision#UNLIMITED}
     */
    protected record SpendPolicy(BigDecimal minimumSpend,
                                 DateRange spendWindow,
                                 boolean spendWaived,
                                 String waiverNote,
                                 int visitsPerQuarter) {
    }

    protected EligibilityDecision applyPolicy(EligibilityContext ctx, SpendPolicy policy, Voucher voucher) {
        Card card = ctx.card();

        // Quota left *before* this attempt (UNLIMITED = -1).
        int quotaLeft = UNLIMITED;
        if (policy.visitsPerQuarter() != UNLIMITED) {
            quotaLeft = (int) Math.max(0, policy.visitsPerQuarter() - consumedVisits(card, ctx.now()));
        }

        String spendNote;
        if (policy.spendWaived()) {
            spendNote = "spend requirement waived (" + policy.waiverNote() + ")";
        } else if (policy.minimumSpend().signum() <= 0) {
            spendNote = "no spend requirement for this tier";
        } else {
            BigDecimal spent = qualifyingSpend(card, policy.spendWindow());
            if (spent.compareTo(policy.minimumSpend()) < 0) {
                return EligibilityDecision.deny(DenialReason.INSUFFICIENT_SPEND,
                        "Qualifying spend " + money(spent) + " between " + policy.spendWindow().start()
                                + " and " + policy.spendWindow().end() + " is below the required "
                                + money(policy.minimumSpend()), Math.max(quotaLeft, 0));
            }
            spendNote = "qualifying spend " + money(spent) + " meets the " + money(policy.minimumSpend())
                    + " threshold";
        }

        if (policy.visitsPerQuarter() == UNLIMITED) {
            return EligibilityDecision.approve("Eligible: " + spendNote + "; unlimited lounge access",
                    UNLIMITED, voucher);
        }
        if (quotaLeft <= 0) {
            return EligibilityDecision.deny(DenialReason.QUOTA_EXCEEDED,
                    "Quarterly quota of " + policy.visitsPerQuarter() + " lounge visit(s) is already used", 0);
        }
        return EligibilityDecision.approve("Eligible: " + spendNote, quotaLeft - 1, voucher);
    }

    protected BigDecimal qualifyingSpend(Card card, DateRange window) {
        BigDecimal total = spendHistoryRepository.sumQualifyingSpend(
                card.getId(), window.start(), window.end(), SpendCategory.nonQualifying());
        return total == null ? BigDecimal.ZERO : total;
    }

    protected long consumedVisits(Card card, LocalDateTime now) {
        DateRange quarter = DateRangeUtil.currentQuarter(now.toLocalDate());
        return loungeVisitRepository.countConsumedVisits(
                card.getId(), quarter.startOfDay(), quarter.endExclusive(), now);
    }

    protected static String money(BigDecimal amount) {
        return "\u20B9" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
