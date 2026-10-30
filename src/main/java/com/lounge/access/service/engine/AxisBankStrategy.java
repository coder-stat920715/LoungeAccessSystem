package com.lounge.access.service.engine;

import com.lounge.access.config.LoungeProperties;
import com.lounge.access.dto.Bank;
import com.lounge.access.entity.Card;
import com.lounge.access.repository.LoungeVisitRepository;
import com.lounge.access.repository.SpendHistoryRepository;
import com.lounge.access.util.DateRangeUtil;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

/**
 * Axis Bank: minimum qualifying spend in the preceding 3 calendar months (cash, wallet reloads and fuel excluded).
 * New-card exception: the requirement is waived through the end of the month that falls
 * {@code newCardWaiverMonths} after the issue month (issued in month M -> waived until the end of M+3).
 */
@Component
public class AxisBankStrategy extends AbstractSpendLinkedStrategy {

    private final LoungeProperties.Axis config;

    public AxisBankStrategy(SpendHistoryRepository spendHistoryRepository,
                            LoungeVisitRepository loungeVisitRepository,
                            LoungeProperties properties) {
        super(spendHistoryRepository, loungeVisitRepository);
        this.config = properties.getAxis();
    }

    @Override
    public String name() {
        return "AXIS_BANK";
    }

    @Override
    public boolean supports(Card card) {
        return card.getBank() == Bank.AXIS;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public EligibilityDecision evaluate(EligibilityContext ctx) {
        LocalDate today = ctx.today();
        LocalDate waiverEnd = YearMonth.from(ctx.card().getIssueDate())
                .plusMonths(config.getNewCardWaiverMonths()).atEndOfMonth();
        boolean waived = !today.isAfter(waiverEnd);

        SpendPolicy policy = new SpendPolicy(
                config.getMinimumSpend(),
                DateRangeUtil.precedingCalendarMonths(today, config.getLookbackMonths()),
                waived,
                "new card exception until " + waiverEnd,
                config.getVisitsPerQuarter());
        return applyPolicy(ctx, policy, null);
    }
}
