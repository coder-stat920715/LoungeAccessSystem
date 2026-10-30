package com.lounge.access.service.statement;

import static com.lounge.access.util.InrFormat.format;

import com.lounge.access.dto.EligibilityStatus;
import com.lounge.access.dto.StatementAnalysisResponse;
import com.lounge.access.dto.StatementAnalysisResponse.CategoryBreakdown;
import com.lounge.access.dto.StatementAnalysisResponse.ExcludedSpend;
import com.lounge.access.dto.StatementAnalysisResponse.FileSummary;
import com.lounge.access.dto.StatementAnalysisResponse.FollowUpPlan;
import com.lounge.access.dto.StatementAnalysisResponse.MerchantSpend;
import com.lounge.access.dto.StatementAnalysisResponse.MonthlyBreakdown;
import com.lounge.access.dto.StatementAnalysisResponse.QuotaInfo;
import com.lounge.access.dto.StatementAnalysisResponse.SpendWindow;
import com.lounge.access.dto.StatementAnalysisResponse.TransactionView;
import com.lounge.access.dto.StatementCategory;
import com.lounge.access.dto.TransactionKind;
import com.lounge.access.util.DateRangeUtil;
import com.lounge.access.util.DateRangeUtil.DateRange;
import com.lounge.access.util.LoungeConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Turns parsed statements into an eligibility forecast for a target month: spend-window maths, category / merchant
 * analysis, quota, a plan for the months after, and plain-language explanations.
 */
@Component
public class StatementAnalyzer {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal WEEKS_PER_MONTH = new BigDecimal("4.33");
    private static final int GAP_TOLERANCE_DAYS = 5;

    private final SpendRuleResolver resolver;

    public StatementAnalyzer(SpendRuleResolver resolver) {
        this.resolver = resolver;
    }

    /** Per-month accumulator. */
    private static final class MonthAcc {
        BigDecimal total = ZERO;
        BigDecimal qualifying = ZERO;
        BigDecimal nonQualifying = ZERO;
        int count;
        final Map<StatementCategory, BigDecimal> byCategory = new EnumMap<>(StatementCategory.class);

        void add(StatementTransaction t) {
            total = total.add(t.amount());
            if (t.category().isQualifying()) {
                qualifying = qualifying.add(t.amount());
            } else {
                nonQualifying = nonQualifying.add(t.amount());
            }
            byCategory.merge(t.category(), t.amount(), BigDecimal::add);
            count++;
        }
    }

    public StatementAnalysisResponse analyze(AnalysisRequest request, List<ParsedStatement> statements) {
        List<String> warnings = new ArrayList<>();
        List<FileSummary> files = statements.stream()
                .map(s -> new FileSummary(s.fileName(), s.format(), s.transactions().size(), s.skippedLines(),
                        s.firstDate(), s.lastDate()))
                .toList();
        statements.stream().filter(s -> s.skippedLines() > 0).forEach(s -> warnings.add(s.skippedLines()
                + " line(s) in " + s.fileName() + " looked like transactions but could not be read; "
                + "totals may be understated. Prefer the CSV export if this is a PDF."));
        List<StatementTransaction> txns = merge(statements, warnings);

        Optional<SpendRule> maybeRule = resolver.resolve(request.bank(), request.cardType(), request.productCode());
        if (maybeRule.isEmpty()) {
            return unsupported(request, files, warnings);
        }
        SpendRule rule = maybeRule.get();
        YearMonth target = request.targetMonth();
        DateRange window = rule.windowFor(target);
        List<YearMonth> windowMonths = rule.windowMonths(target);

        // ---- aggregate
        Map<YearMonth, MonthAcc> months = new TreeMap<>();
        Set<YearMonth> monthsWithData = new HashSet<>();
        BigDecimal payments = ZERO;
        BigDecimal refunds = ZERO;
        for (StatementTransaction t : txns) {
            YearMonth ym = YearMonth.from(t.date());
            monthsWithData.add(ym);
            if (t.kind() == TransactionKind.PAYMENT) {
                payments = payments.add(t.amount());
            } else if (t.kind() == TransactionKind.REFUND) {
                refunds = refunds.add(t.amount());
            } else {
                months.computeIfAbsent(ym, k -> new MonthAcc()).add(t);
            }
        }
        List<StatementTransaction> purchases = txns.stream().filter(t -> t.kind() == TransactionKind.PURCHASE).toList();

        BigDecimal qualifyingInWindow = windowMonths.stream().map(m -> qualifying(months, m))
                .reduce(ZERO, BigDecimal::add);
        boolean dataComplete = rule.minimumSpend().signum() == 0
                || checkCoverage(txns, monthsWithData, window, windowMonths, warnings);
        if (refunds.signum() > 0) {
            warnings.add("Refunds/reversals worth " + format(refunds) + " were found. They are not netted off; if they "
                    + "relate to qualifying purchases, your true qualifying spend may be lower.");
        }

        // ---- verdict
        BigDecimal min = rule.minimumSpend();
        boolean spendMet = qualifyingInWindow.compareTo(min) >= 0;
        boolean waiver = rule.newCardWaiverMonths() >= 0 && request.cardIssueDate() != null
                && !target.isAfter(YearMonth.from(request.cardIssueDate()).plusMonths(rule.newCardWaiverMonths()));
        LocalDate waiverEnd = rule.newCardWaiverMonths() >= 0 && request.cardIssueDate() != null
                ? YearMonth.from(request.cardIssueDate()).plusMonths(rule.newCardWaiverMonths()).atEndOfMonth() : null;

        EligibilityStatus status;
        if (min.signum() == 0 || spendMet) {
            status = EligibilityStatus.ELIGIBLE;
        } else if (waiver) {
            status = EligibilityStatus.ELIGIBLE_NEW_CARD_EXCEPTION;
        } else {
            status = EligibilityStatus.NOT_ELIGIBLE;
        }

        DateRange quarter = DateRangeUtil.currentQuarter(target.atDay(1));
        boolean unlimited = rule.visitsPerQuarter() == LoungeConstants.UNLIMITED_VISITS;
        int available = unlimited ? LoungeConstants.UNLIMITED_VISITS
                : Math.max(0, rule.visitsPerQuarter() - request.visitsUsedInTargetQuarter());
        if (status != EligibilityStatus.NOT_ELIGIBLE && available == 0) {
            status = EligibilityStatus.QUOTA_EXHAUSTED;
        }
        boolean eligible = status == EligibilityStatus.ELIGIBLE || status == EligibilityStatus.ELIGIBLE_NEW_CARD_EXCEPTION;
        QuotaInfo quota = new QuotaInfo(rule.visitsPerQuarter(), quarter.start(), quarter.end(),
                request.visitsUsedInTargetQuarter(), available, rule.voucherRequired(),
                unlimited ? "Unlimited visits for this tier."
                        : available + " of " + rule.visitsPerQuarter() + " visit(s) left in " + quarterLabel(quarter.start())
                        + (rule.voucherRequired() ? "; a 12-digit voucher must be generated before each visit." : "."));

        BigDecimal diff = qualifyingInWindow.subtract(min);
        double progress = min.signum() == 0 ? 100.0
                : qualifyingInWindow.multiply(HUNDRED).divide(min, 1, RoundingMode.HALF_UP).min(HUNDRED).doubleValue();

        // ---- analytics
        BigDecimal allPurchases = purchases.stream().map(StatementTransaction::amount).reduce(ZERO, BigDecimal::add);
        List<MonthlyBreakdown> monthly = monthlyBreakdown(months, monthsWithData, windowMonths);
        List<CategoryBreakdown> categories = categoryBreakdown(purchases, allPurchases);
        List<MerchantSpend> merchants = topMerchants(purchases);
        ExcludedSpend excluded = excludedSpend(purchases, allPurchases);
        FollowUpPlan plan = buildPlan(rule, target, months, windowMonths, request);

        List<String> insights = insights(months, windowMonths, purchases, allPurchases, excluded, merchants);
        List<String> recommendations = recommendations(status, rule, target, qualifyingInWindow, min, plan, excluded,
                categories, available, waiverEnd, dataComplete, windowMonths, months);
        String summary = summary(status, rule, target, window, qualifyingInWindow, min, plan, available);

        return new StatementAnalysisResponse(
                request.bank().name(), request.cardType().name(), request.productCode(), target.toString(),
                rule.name(), rule.description(), status, eligible, summary,
                new SpendWindow(window.start(), window.end(), windowMonths.stream().map(YearMonth::toString).toList()),
                scale(qualifyingInWindow), scale(min), scale(diff), progress, dataComplete, quota, monthly, categories,
                merchants, excluded, plan, insights, recommendations, warnings, assumptions(rule), files);
    }

    // ------------------------------------------------------------------ merge / coverage

    private List<StatementTransaction> merge(List<ParsedStatement> statements, List<String> warnings) {
        Map<String, Integer> firstSeenIn = new HashMap<>();
        List<StatementTransaction> merged = new ArrayList<>();
        int duplicates = 0;
        for (int i = 0; i < statements.size(); i++) {
            for (StatementTransaction t : statements.get(i).transactions()) {
                String key = t.date() + "|" + t.description().toUpperCase(Locale.ROOT) + "|" + t.amount().stripTrailingZeros()
                        + "|" + t.kind();
                Integer seenIn = firstSeenIn.putIfAbsent(key, i);
                if (seenIn != null && seenIn != i) {
                    duplicates++;
                    continue;
                }
                merged.add(t);
            }
        }
        if (duplicates > 0) {
            warnings.add(duplicates + " transaction(s) appeared in more than one file (overlapping statement cycles) "
                    + "and were counted once.");
        }
        return merged;
    }

    private boolean checkCoverage(List<StatementTransaction> txns, Set<YearMonth> monthsWithData, DateRange window,
                                  List<YearMonth> windowMonths, List<String> warnings) {
        boolean complete = true;
        for (YearMonth m : windowMonths) {
            if (!monthsWithData.contains(m)) {
                complete = false;
                warnings.add("No transactions found for " + monthName(m) + ", which is part of the spend window ("
                        + window.start() + " to " + window.end() + "). Attach that month's statement for an accurate result.");
            }
        }
        if (!txns.isEmpty()) {
            LocalDate first = txns.stream().map(StatementTransaction::date).min(LocalDate::compareTo).orElseThrow();
            LocalDate last = txns.stream().map(StatementTransaction::date).max(LocalDate::compareTo).orElseThrow();
            if (windowMonths.contains(YearMonth.from(first)) && first.isAfter(window.start().plusDays(GAP_TOLERANCE_DAYS))) {
                complete = false;
                warnings.add("Statements begin on " + first + " but the spend window starts on " + window.start()
                        + "; spends in between are not included.");
            }
            if (windowMonths.contains(YearMonth.from(last)) && last.isBefore(window.end().minusDays(GAP_TOLERANCE_DAYS))) {
                complete = false;
                warnings.add("Statements end on " + last + " but the spend window runs to " + window.end()
                        + "; later spends are not included.");
            }
        }
        return complete;
    }

    // ------------------------------------------------------------------ analytics blocks

    private List<MonthlyBreakdown> monthlyBreakdown(Map<YearMonth, MonthAcc> months, Set<YearMonth> withData,
                                                    List<YearMonth> windowMonths) {
        Set<YearMonth> all = new java.util.TreeSet<>(months.keySet());
        all.addAll(windowMonths);
        all.addAll(withData);
        List<MonthlyBreakdown> rows = new ArrayList<>();
        for (YearMonth m : all) {
            MonthAcc a = months.get(m);
            Map<String, BigDecimal> byCat = new LinkedHashMap<>();
            if (a != null) {
                a.byCategory.entrySet().stream().sorted(Map.Entry.<StatementCategory, BigDecimal>comparingByValue().reversed())
                        .forEach(e -> byCat.put(e.getKey().name(), scale(e.getValue())));
            }
            rows.add(new MonthlyBreakdown(m.toString(), monthName(m), withData.contains(m), windowMonths.contains(m),
                    a == null ? 0 : a.count, scale(a == null ? ZERO : a.total), scale(a == null ? ZERO : a.qualifying),
                    scale(a == null ? ZERO : a.nonQualifying), byCat));
        }
        return rows;
    }

    private List<CategoryBreakdown> categoryBreakdown(List<StatementTransaction> purchases, BigDecimal total) {
        Map<StatementCategory, BigDecimal> sums = new EnumMap<>(StatementCategory.class);
        Map<StatementCategory, Integer> counts = new EnumMap<>(StatementCategory.class);
        for (StatementTransaction t : purchases) {
            sums.merge(t.category(), t.amount(), BigDecimal::add);
            counts.merge(t.category(), 1, Integer::sum);
        }
        return sums.entrySet().stream()
                .sorted(Map.Entry.<StatementCategory, BigDecimal>comparingByValue().reversed())
                .map(e -> new CategoryBreakdown(e.getKey().name(), e.getKey().isQualifying(), scale(e.getValue()),
                        percent(e.getValue(), total), counts.get(e.getKey())))
                .toList();
    }

    private List<MerchantSpend> topMerchants(List<StatementTransaction> purchases) {
        List<StatementTransaction> qualifying = purchases.stream().filter(t -> t.category().isQualifying()).toList();
        BigDecimal total = qualifying.stream().map(StatementTransaction::amount).reduce(ZERO, BigDecimal::add);
        Map<String, BigDecimal> sums = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (StatementTransaction t : qualifying) {
            String key = merchantKey(t.description());
            sums.merge(key, t.amount(), BigDecimal::add);
            counts.merge(key, 1, Integer::sum);
        }
        return sums.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .map(e -> new MerchantSpend(e.getKey(), scale(e.getValue()), counts.get(e.getKey()), percent(e.getValue(), total)))
                .toList();
    }

    private ExcludedSpend excludedSpend(List<StatementTransaction> purchases, BigDecimal allPurchases) {
        List<StatementTransaction> excluded = purchases.stream().filter(t -> !t.category().isQualifying()).toList();
        BigDecimal total = excluded.stream().map(StatementTransaction::amount).reduce(ZERO, BigDecimal::add);
        Map<StatementCategory, BigDecimal> by = new EnumMap<>(StatementCategory.class);
        excluded.forEach(t -> by.merge(t.category(), t.amount(), BigDecimal::add));
        Map<String, BigDecimal> byName = new LinkedHashMap<>();
        by.entrySet().stream().sorted(Map.Entry.<StatementCategory, BigDecimal>comparingByValue().reversed())
                .forEach(e -> byName.put(e.getKey().name(), scale(e.getValue())));
        List<TransactionView> largest = excluded.stream()
                .sorted(Comparator.comparing(StatementTransaction::amount).reversed())
                .limit(5)
                .map(t -> new TransactionView(t.date(), t.description(), scale(t.amount()), t.category().name()))
                .toList();
        return new ExcludedSpend(scale(total), percent(total, allPurchases), byName, largest);
    }

    // ------------------------------------------------------------------ forward plan

    private FollowUpPlan buildPlan(SpendRule rule, YearMonth target, Map<YearMonth, MonthAcc> months,
                                   List<YearMonth> targetWindowMonths, AnalysisRequest request) {
        YearMonth following = target.plusMonths(1);
        YearMonth plan = following;
        for (int i = 1; i <= 6; i++) {
            YearMonth candidate = target.plusMonths(i);
            if (rule.windowMonths(candidate).contains(target)) {
                plan = candidate;
                break;
            }
        }
        DateRange planWindow = rule.windowFor(plan);
        List<YearMonth> planMonths = rule.windowMonths(plan);
        List<YearMonth> completed = planMonths.stream().filter(m -> m.isBefore(target)).toList();
        List<YearMonth> future = planMonths.stream().filter(m -> !m.isBefore(target)).toList();
        BigDecimal banked = completed.stream().map(m -> qualifying(months, m)).reduce(ZERO, BigDecimal::add);
        BigDecimal needed = rule.minimumSpend().subtract(banked).max(ZERO);
        BigDecimal perMonth = future.isEmpty() ? ZERO
                : needed.divide(BigDecimal.valueOf(future.size()), 0, RoundingMode.CEILING);
        BigDecimal perWeek = perMonth.divide(WEEKS_PER_MONTH, 0, RoundingMode.CEILING);
        List<YearMonth> leaving = targetWindowMonths.stream().filter(m -> !planMonths.contains(m)).toList();
        BigDecimal leavingSpend = leaving.stream().map(m -> qualifying(months, m)).reduce(ZERO, BigDecimal::add);

        String outlook;
        if (plan.equals(following)) {
            outlook = monthName(following) + " uses " + rangeLabel(rule.windowMonths(following))
                    + ", which includes " + monthName(target) + ", so what you spend in " + monthName(target)
                    + " directly decides " + monthName(following) + ".";
        } else {
            List<YearMonth> fw = rule.windowMonths(following);
            BigDecimal fwSpend = fw.stream().filter(m -> m.isBefore(target)).map(m -> qualifying(months, m))
                    .reduce(ZERO, BigDecimal::add);
            boolean closed = fw.stream().allMatch(m -> m.isBefore(target));
            outlook = monthName(following) + " uses " + rangeLabel(fw) + (closed
                    ? " (already closed): qualifying spend is fixed at " + format(fwSpend) + " vs "
                    + format(rule.minimumSpend()) + " required, so spending in " + monthName(target)
                    + " cannot change it. The first month your " + monthName(target) + " spending counts is "
                    + monthName(plan) + "."
                    : ". The first month your " + monthName(target) + " spending counts is " + monthName(plan) + ".");
        }
        return new FollowUpPlan(plan.toString(), plan.equals(following), planWindow.start(), planWindow.end(),
                planMonths.stream().map(YearMonth::toString).toList(),
                completed.stream().map(YearMonth::toString).toList(),
                future.stream().map(YearMonth::toString).toList(),
                scale(banked), scale(rule.minimumSpend()), scale(needed), scale(perMonth), scale(perWeek),
                needed.signum() == 0, leaving.stream().map(YearMonth::toString).toList(), scale(leavingSpend), outlook);
    }

    // ------------------------------------------------------------------ narrative

    private String summary(EligibilityStatus status, SpendRule rule, YearMonth target, DateRange window,
                           BigDecimal qualifying, BigDecimal min, FollowUpPlan plan, int available) {
        String month = monthName(target);
        String spendText = min.signum() == 0 ? "This tier has no spend requirement."
                : "Qualifying spend between " + window.start() + " and " + window.end() + " is " + format(qualifying)
                + " against the required " + format(min) + ".";
        switch (status) {
            case ELIGIBLE:
                return "You are eligible for lounge access in " + month + ". " + spendText
                        + (available == LoungeConstants.UNLIMITED_VISITS ? "" : " " + available + " visit(s) available this quarter.")
                        + (plan.alreadySecured() ? " You have also already banked enough for " + monthName(YearMonth.parse(plan.planMonth())) + "."
                        : " To stay eligible in " + monthName(YearMonth.parse(plan.planMonth())) + " you need "
                        + format(plan.additionalQualifyingSpendNeeded()) + " more qualifying spend.");
            case ELIGIBLE_NEW_CARD_EXCEPTION:
                return "You are eligible for lounge access in " + month + " only because of the new-card exception. "
                        + spendText + " The exception does not last, so build up qualifying spend.";
            case QUOTA_EXHAUSTED:
                return "Your spend meets the criteria for " + month + " but all lounge visits of the quarter are already used.";
            default:
                return "You are NOT eligible for lounge access in " + month + ". " + spendText + " Shortfall: "
                        + format(min.subtract(qualifying)) + ". "
                        + (plan.planMonthIsFollowingMonth() ? "" : plan.followingMonthOutlook() + " ") + "To qualify in "
                        + monthName(YearMonth.parse(plan.planMonth())) + " you need "
                        + format(plan.additionalQualifyingSpendNeeded()) + " of qualifying spend across "
                        + rangeLabel(plan.monthsStillToCome().stream().map(YearMonth::parse).toList()) + ".";
        }
    }

    private List<String> recommendations(EligibilityStatus status, SpendRule rule, YearMonth target, BigDecimal qualifying,
                                         BigDecimal min, FollowUpPlan plan, ExcludedSpend excluded,
                                         List<CategoryBreakdown> categories, int available, LocalDate waiverEnd,
                                         boolean dataComplete, List<YearMonth> windowMonths,
                                         Map<YearMonth, MonthAcc> months) {
        List<String> r = new ArrayList<>();
        YearMonth planMonth = YearMonth.parse(plan.planMonth());
        String targetName = monthName(target);
        String planName = monthName(planMonth);
        String futureRange = rangeLabel(plan.monthsStillToCome().stream().map(YearMonth::parse).toList());
        String topQualifying = categories.stream().filter(CategoryBreakdown::qualifying).limit(3)
                .map(c -> c.category().toLowerCase(Locale.ROOT).replace('_', ' ')).reduce((a, b) -> a + ", " + b).orElse("everyday spends");

        switch (status) {
            case ELIGIBLE -> {
                r.add("You already meet the criteria: " + (min.signum() == 0 ? "no spend threshold applies to this tier."
                        : format(qualifying) + " qualifying spend vs " + format(min) + " required (surplus "
                        + format(qualifying.subtract(min)) + ")."));
                r.add("Use the lounge: carry the physical card and your customer ID to the reception desk; the desk system "
                        + "verifies it through POST /api/v1/lounge/check-eligibility. " + (rule.voucherRequired()
                        ? "Generate your 12-digit digital voucher BEFORE arriving - entry is refused without a valid, unused voucher. "
                        : "") + (available == LoungeConstants.UNLIMITED_VISITS ? "Visits are unlimited."
                        : "You have " + available + " visit(s) left this quarter - redeem the pass at the desk so the visit is recorded."));
                if (min.signum() > 0) {
                    if (plan.alreadySecured()) {
                        r.add("You have already banked enough spend for " + planName + " (" + format(plan.spendAlreadyBanked())
                                + " vs " + format(min) + "), so no extra spend is required to stay eligible.");
                    } else {
                        r.add("To stay eligible in " + planName + ", add at least " + format(plan.additionalQualifyingSpendNeeded())
                                + " of qualifying spend during " + futureRange + " (about "
                                + format(plan.perMonthTarget()) + " per month / " + format(plan.perWeekTarget()) + " per week). "
                                + (plan.planMonthIsFollowingMonth() ? "" : plan.followingMonthOutlook()));
                    }
                    if (!plan.monthsLeavingWindow().isEmpty() && plan.spendLeavingWindow().signum() > 0) {
                        r.add(rangeLabel(plan.monthsLeavingWindow().stream().map(YearMonth::parse).toList()) + " drops out of the window ("
                                + format(plan.spendLeavingWindow()) + " of today's qualifying spend) - eligibility is rolling, not permanent.");
                    }
                }
            }
            case ELIGIBLE_NEW_CARD_EXCEPTION -> {
                r.add("You qualify in " + targetName + " through the new-card exception" + (waiverEnd == null ? "" : " (valid until "
                        + waiverEnd + ")") + ", even though qualifying spend is " + format(qualifying) + " vs " + format(min) + ".");
                r.add("Start building qualifying spend now: after the exception ends you need " + format(plan.requiredSpend())
                        + " in the rolling window. Banked so far for " + planName + ": " + format(plan.spendAlreadyBanked())
                        + "; target " + format(plan.additionalQualifyingSpendNeeded()) + " more across " + futureRange + ".");
            }
            case QUOTA_EXHAUSTED -> {
                r.add("Spend criteria are met, but the quarterly visit quota is used up. Visits reset at the start of the next quarter.");
                r.add("Keep qualifying spend on track for the next quarter: " + plan.followingMonthOutlook());
            }
            default -> {
                r.add("Not eligible in " + targetName + ": qualifying spend " + format(qualifying) + " is "
                        + format(min.subtract(qualifying)) + " short of " + format(min) + ". The window ("
                        + rangeLabel(windowMonths) + ") is closed, so new spend in " + targetName + " will not change " + targetName + ".");
                int step = 1;
                if (plan.alreadySecured()) {
                    r.add("Good news: " + planName + " is already secured - the months in its window already hold "
                            + format(plan.spendAlreadyBanked()) + ". No additional spend is needed.");
                } else {
                    r.add("Step " + step++ + " - To be eligible in " + planName + ": make at least "
                            + format(plan.additionalQualifyingSpendNeeded()) + " of QUALIFYING spend during " + futureRange
                            + " (about " + format(plan.perMonthTarget()) + " per month, roughly " + format(plan.perWeekTarget())
                            + " per week). Already banked for that window: " + format(plan.spendAlreadyBanked()) + " ("
                            + rangeLabel(plan.monthsAlreadyCompleted().stream().map(YearMonth::parse).toList()) + ").");
                    r.add("Step " + step++ + " - Spend on categories that count (your strongest so far: " + topQualifying
                            + "). Pay recurring bills, groceries, dining, travel and shopping on this card instead of UPI/other cards.");
                }
                if (excluded.total().signum() > 0) {
                    r.add("Step " + step++ + " - Stop wasting spend: " + format(excluded.total()) + " ("
                            + excluded.percentOfAllSpend() + "% of everything you charged) does not count: "
                            + describe(excluded.byCategory())
                            + ". Pay for fuel, wallet top-ups and cash needs with another instrument and keep this card for qualifying merchants.");
                }
                if (!plan.planMonthIsFollowingMonth()) {
                    r.add("Step " + step++ + " - " + plan.followingMonthOutlook());
                }
                r.add("Step " + step + " - On the 1st of " + planName + ", re-upload your statements with targetMonth=" + planMonth
                        + " (including the newest month) to confirm; then check eligibility at the desk with POST /api/v1/lounge/check-eligibility.");
            }
        }
        if (!dataComplete && (status == EligibilityStatus.NOT_ELIGIBLE || status == EligibilityStatus.ELIGIBLE_NEW_CARD_EXCEPTION)) {
            r.add("Data note: your statements do not fully cover the spend window, so these figures may be understated. "
                    + "Upload every statement for " + rangeLabel(windowMonths) + " to confirm.");
        }
        return r;
    }

    private List<String> insights(Map<YearMonth, MonthAcc> months, List<YearMonth> windowMonths,
                                  List<StatementTransaction> purchases, BigDecimal allPurchases, ExcludedSpend excluded,
                                  List<MerchantSpend> merchants) {
        List<String> out = new ArrayList<>();
        List<YearMonth> withSpend = months.keySet().stream().sorted().toList();
        if (withSpend.size() >= 2) {
            YearMonth last = withSpend.get(withSpend.size() - 1);
            YearMonth prev = withSpend.get(withSpend.size() - 2);
            BigDecimal a = months.get(prev).qualifying;
            BigDecimal b = months.get(last).qualifying;
            if (a.signum() > 0) {
                BigDecimal change = b.subtract(a).multiply(HUNDRED).divide(a, 1, RoundingMode.HALF_UP);
                out.add("Qualifying spend " + (change.signum() >= 0 ? "rose " : "fell ") + change.abs() + "% from "
                        + monthName(prev) + " (" + format(a) + ") to " + monthName(last) + " (" + format(b) + ").");
            }
        }
        if (!withSpend.isEmpty()) {
            BigDecimal totalQ = withSpend.stream().map(m -> months.get(m).qualifying).reduce(ZERO, BigDecimal::add);
            BigDecimal avg = totalQ.divide(BigDecimal.valueOf(withSpend.size()), 0, RoundingMode.HALF_UP);
            out.add("Average qualifying spend is " + format(avg) + " per month across " + withSpend.size() + " month(s) with data.");
        }
        if (allPurchases.signum() > 0) {
            out.add(excluded.percentOfAllSpend() + "% of your card spend (" + format(excluded.total())
                    + ") is on categories that never count toward lounge access.");
        }
        List<StatementTransaction> qualifying = purchases.stream().filter(t -> t.category().isQualifying()).toList();
        BigDecimal totalQualifying = qualifying.stream().map(StatementTransaction::amount).reduce(ZERO, BigDecimal::add);
        qualifying.stream().max(Comparator.comparing(StatementTransaction::amount)).ifPresent(top -> {
            if (totalQualifying.signum() > 0) {
                BigDecimal share = top.amount().multiply(HUNDRED).divide(totalQualifying, 1, RoundingMode.HALF_UP);
                out.add("Largest qualifying purchase: " + format(top.amount()) + " at " + merchantKey(top.description()) + " on "
                        + top.date() + " (" + share + "% of qualifying spend)"
                        + (share.compareTo(new BigDecimal("40")) > 0
                        ? " - eligibility leans heavily on one purchase, so a refund or return could flip the result." : "."));
            }
        });
        if (!merchants.isEmpty()) {
            MerchantSpend m = merchants.get(0);
            out.add("Top qualifying merchant: " + m.merchant() + " (" + format(m.amount()) + ", " + m.percentOfQualifyingSpend()
                    + "% of qualifying spend).");
        }
        return out;
    }

    private List<String> assumptions(SpendRule rule) {
        List<String> a = new ArrayList<>();
        a.add("Spend is grouped by transaction date into calendar months, regardless of statement cycle dates.");
        a.add("Cash withdrawals, wallet reloads, fuel and fees/interest/GST do not count toward the threshold; all other purchases do.");
        a.add("Payments and refunds are credits and are never counted as spend (refunds are not netted off).");
        a.add("Rule applied: " + rule.description());
        a.add("Categories are inferred from merchant names and any category column in the statement; unknown merchants count as OTHER (qualifying).");
        a.add("This is a forecast from the statements you supplied; the bank's own ledger and the desk check are authoritative.");
        return a;
    }

    private StatementAnalysisResponse unsupported(AnalysisRequest r, List<FileSummary> files, List<String> warnings) {
        String msg = "No lounge programme is configured for " + r.bank() + " " + r.cardType() + " " + r.productCode()
                + ", so eligibility cannot be forecast.";
        return new StatementAnalysisResponse(r.bank().name(), r.cardType().name(), r.productCode(), r.targetMonth().toString(),
                "NONE", msg, EligibilityStatus.NOT_SUPPORTED, false, msg, null, ZERO, ZERO, ZERO, 0.0, false, null,
                List.of(), List.of(), List.of(), null, null, List.of(),
                List.of("Check the bank/card product spelling, or ask your bank which lounge programme applies to this card."),
                warnings, List.of("Supported: Axis (any card), HDFC debit, HDFC Millennia / Regalia Gold / Infinia / Diners Black, "
                + "and the banks listed in lounge.generic.supported-banks."), files);
    }

    // ------------------------------------------------------------------ helpers

    private static BigDecimal qualifying(Map<YearMonth, MonthAcc> months, YearMonth m) {
        MonthAcc a = months.get(m);
        return a == null ? ZERO : a.qualifying;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static double percent(BigDecimal part, BigDecimal total) {
        return total.signum() == 0 ? 0.0 : part.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String describe(Map<String, BigDecimal> byCategory) {
        return byCategory.entrySet().stream()
                .map(e -> e.getKey().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + format(e.getValue()))
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    static String merchantKey(String description) {
        String cleaned = description.toUpperCase(Locale.ROOT).replaceAll("[0-9*#]+", " ").replaceAll("\\s+", " ").trim();
        String[] tokens = cleaned.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, tokens.length); i++) {
            sb.append(i > 0 ? " " : "").append(tokens[i]);
        }
        return sb.length() == 0 ? "UNKNOWN" : sb.toString();
    }

    static String monthName(YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
    }

    private static String rangeLabel(List<YearMonth> months) {
        if (months.isEmpty()) {
            return "n/a";
        }
        if (months.size() == 1) {
            return monthName(months.get(0));
        }
        return monthName(months.get(0)) + " - " + monthName(months.get(months.size() - 1));
    }

    private static String quarterLabel(LocalDate quarterStart) {
        return "Q" + ((quarterStart.getMonthValue() - 1) / 3 + 1) + " " + quarterStart.getYear();
    }
}
