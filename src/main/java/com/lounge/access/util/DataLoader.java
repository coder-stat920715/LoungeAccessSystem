package com.lounge.access.util;

import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardScheme;
import com.lounge.access.dto.CardType;
import com.lounge.access.dto.DenialReason;
import com.lounge.access.dto.SpendCategory;
import com.lounge.access.dto.VisitStatus;
import com.lounge.access.entity.Card;
import com.lounge.access.entity.Customer;
import com.lounge.access.entity.LoungeVisit;
import com.lounge.access.entity.SpendHistory;
import com.lounge.access.entity.Voucher;
import com.lounge.access.repository.CardRepository;
import com.lounge.access.repository.CustomerRepository;
import com.lounge.access.repository.LoungeVisitRepository;
import com.lounge.access.repository.SpendHistoryRepository;
import com.lounge.access.repository.VoucherRepository;
import com.lounge.access.util.DateRangeUtil.DateRange;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds demo customers, cards, spend history, vouchers and a few historical visits so the application is
 * usable out of the box. All dates are relative to "today", so the rules always have data in their windows.
 * Disable with {@code lounge.bootstrap.enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lounge.bootstrap", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataLoader implements CommandLineRunner {

    // Fake demo PANs (not real card numbers).
    public static final String AXIS_ACE_QUALIFYING = "4147200000000011";
    public static final String AXIS_ACE_NEW_CARD = "4147200000000029";
    public static final String AXIS_ACE_LOW_SPEND = "4147200000000037";
    public static final String HDFC_DEBIT_QUALIFYING = "4386100000000013";
    public static final String HDFC_DEBIT_LOW_SPEND = "4386100000000021";
    public static final String HDFC_INFINIA = "4386200000000019";
    public static final String HDFC_MILLENNIA_QUOTA_USED = "5241900000000012";
    public static final String HDFC_REGALIA_GOLD = "5241910000000010";
    public static final String ICICI_CORAL = "6521000000000012";
    public static final String KOTAK_UNSUPPORTED = "4000100000000018";

    public static final String VOUCHER_VALID = "482915037261";
    public static final String VOUCHER_EXPIRED = "719304286150";
    public static final String VOUCHER_USED = "260481937502";
    public static final String VOUCHER_LOW_SPEND_CARD = "530918274466";

    private static final String LOUNGE = "HYD-T1-ENCORE";

    private final CustomerRepository customerRepository;
    private final CardRepository cardRepository;
    private final SpendHistoryRepository spendHistoryRepository;
    private final VoucherRepository voucherRepository;
    private final LoungeVisitRepository loungeVisitRepository;
    private final CardNumberHasher hasher;
    private final Clock clock;

    private final List<SpendHistory> spends = new ArrayList<>();

    @Override
    @Transactional
    public void run(String... args) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        DateRange prevQuarter = DateRangeUtil.previousQuarter(today);
        LocalDate pq = prevQuarter.start();
        LocalDate thisMonth = today.withDayOfMonth(1);
        spends.clear();

        // ---- Axis ACE: qualifying (62,000 in preceding 3 calendar months)
        Card aaravAce = card(customer("Aarav Sharma", "aarav.sharma@example.com", "9810000001"),
                AXIS_ACE_QUALIFYING, Bank.AXIS, CardScheme.VISA, CardType.CREDIT, "ACE", today.minusMonths(14));
        spend(aaravAce, thisMonth.minusMonths(1).plusDays(9), "35000", SpendCategory.RETAIL, "Amazon");
        spend(aaravAce, thisMonth.minusMonths(2).plusDays(9), "18000", SpendCategory.DINING, "Taj Hotels");
        spend(aaravAce, thisMonth.minusMonths(3).plusDays(9), "9000", SpendCategory.TRAVEL, "IndiGo");

        // ---- Axis ACE: brand-new card, no spend -> New Card Exception
        Card meeraAce = card(customer("Meera Iyer", "meera.iyer@example.com", "9810000002"),
                AXIS_ACE_NEW_CARD, Bank.AXIS, CardScheme.VISA, CardType.CREDIT, "ACE", today.minusDays(20));

        // ---- Axis ACE: only 20,000 qualifies (fuel/cash/wallet excluded, current month outside the window)
        Card rohanAce = card(customer("Rohan Gupta", "rohan.gupta@example.com", "9810000003"),
                AXIS_ACE_LOW_SPEND, Bank.AXIS, CardScheme.VISA, CardType.CREDIT, "ACE", today.minusMonths(18));
        spend(rohanAce, thisMonth.minusMonths(1).plusDays(9), "20000", SpendCategory.RETAIL, "Flipkart");
        spend(rohanAce, thisMonth.minusMonths(2).plusDays(9), "25000", SpendCategory.FUEL, "HPCL");
        spend(rohanAce, thisMonth.minusMonths(3).plusDays(9), "15000", SpendCategory.CASH_WITHDRAWAL, "ATM");
        spend(rohanAce, thisMonth.minusMonths(1).plusDays(14), "6000", SpendCategory.WALLET_RELOAD, "Paytm");
        spend(rohanAce, thisMonth.plusDays(1), "40000", SpendCategory.RETAIL, "Croma");

        // ---- HDFC Platinum Debit: 14,500 qualifying + valid / expired / used vouchers
        Card vikramDebit = card(customer("Vikram Singh", "vikram.singh@example.com", "9810000004"),
                HDFC_DEBIT_QUALIFYING, Bank.HDFC, CardScheme.VISA, CardType.DEBIT, "PLATINUM_DEBIT",
                today.minusYears(3));
        spend(vikramDebit, pq.plusDays(10), "5000", SpendCategory.RETAIL, "Reliance Digital");
        spend(vikramDebit, pq.plusDays(35), "6500", SpendCategory.GROCERY, "BigBasket");
        spend(vikramDebit, pq.plusDays(60), "3000", SpendCategory.DINING, "Barbeque Nation");
        spend(vikramDebit, pq.plusDays(40), "20000", SpendCategory.CASH_WITHDRAWAL, "ATM");
        voucher(vikramDebit, VOUCHER_VALID, now.minusHours(2), now.plusHours(22), false);
        voucher(vikramDebit, VOUCHER_EXPIRED, now.minusDays(3), now.minusDays(2), false);
        voucher(vikramDebit, VOUCHER_USED, now.minusDays(40), now.minusDays(39), true);

        // ---- HDFC Platinum Debit: only 6,500 qualifying (10,000 wallet reload excluded)
        Card snehaDebit = card(customer("Sneha Reddy", "sneha.reddy@example.com", "9810000005"),
                HDFC_DEBIT_LOW_SPEND, Bank.HDFC, CardScheme.RUPAY, CardType.DEBIT, "PLATINUM_DEBIT",
                today.minusYears(2));
        spend(snehaDebit, pq.plusDays(12), "4000", SpendCategory.RETAIL, "Myntra");
        spend(snehaDebit, pq.plusDays(30), "2500", SpendCategory.UTILITIES, "BESCOM");
        spend(snehaDebit, pq.plusDays(50), "10000", SpendCategory.WALLET_RELOAD, "PhonePe");
        voucher(snehaDebit, VOUCHER_LOW_SPEND_CARD, now.minusHours(1), now.plusHours(23), false);

        // ---- HDFC Infinia: unlimited, no spend threshold
        Card ananyaInfinia = card(customer("Ananya Kapoor", "ananya.kapoor@example.com", "9810000006"),
                HDFC_INFINIA, Bank.HDFC, CardScheme.VISA, CardType.CREDIT, "INFINIA", today.minusYears(4));
        spend(ananyaInfinia, pq.plusDays(20), "250000", SpendCategory.TRAVEL, "Emirates");

        // ---- HDFC Millennia: qualifies on spend but its single quarterly visit is already used
        Card karanMillennia = card(customer("Karan Malhotra", "karan.malhotra@example.com", "9810000007"),
                HDFC_MILLENNIA_QUOTA_USED, Bank.HDFC, CardScheme.MASTERCARD, CardType.CREDIT, "MILLENNIA",
                today.minusYears(2));
        spend(karanMillennia, pq.plusDays(15), "70000", SpendCategory.RETAIL, "Apple Store");
        spend(karanMillennia, pq.plusDays(45), "50000", SpendCategory.DINING, "ITC Hotels");

        // ---- HDFC Regalia Gold: 75,000 >= 60,000, 3 visits per quarter
        Card ishitaRegalia = card(customer("Ishita Rao", "ishita.rao@example.com", "9810000008"),
                HDFC_REGALIA_GOLD, Bank.HDFC, CardScheme.MASTERCARD, CardType.CREDIT, "REGALIA_GOLD",
                today.minusYears(1));
        spend(ishitaRegalia, pq.plusDays(18), "45000", SpendCategory.TRAVEL, "MakeMyTrip");
        spend(ishitaRegalia, pq.plusDays(55), "30000", SpendCategory.RETAIL, "Tanishq");

        // ---- ICICI (generic fallback): 30,000 >= 25,000, 2 visits per quarter
        Card divyaCoral = card(customer("Divya Menon", "divya.menon@example.com", "9810000009"),
                ICICI_CORAL, Bank.ICICI, CardScheme.RUPAY, CardType.CREDIT, "CORAL", today.minusYears(2));
        spend(divyaCoral, pq.plusDays(22), "18000", SpendCategory.RETAIL, "Westside");
        spend(divyaCoral, pq.plusDays(48), "12000", SpendCategory.GROCERY, "Nature's Basket");

        // ---- Kotak: no configured programme -> UNSUPPORTED_CARD
        Card imranKotak = card(customer("Imran Khan", "imran.khan@example.com", "9810000010"),
                KOTAK_UNSUPPORTED, Bank.KOTAK, CardScheme.VISA, CardType.DEBIT, "811", today.minusYears(1));
        spend(imranKotak, pq.plusDays(25), "50000", SpendCategory.RETAIL, "Decathlon");

        spendHistoryRepository.saveAll(spends);

        // ---- Historical visits (feed the analytics endpoints and exercise quota rules)
        visit(karanMillennia, VisitStatus.REDEEMED, null, "Eligible: seeded visit", 0, "LP-SEEDMILLENNIA00001",
                inCurrentQuarter(now.minusHours(3), now));
        visit(divyaCoral, VisitStatus.REDEEMED, null, "Eligible: seeded visit", 1, "LP-SEEDICICICORAL0001",
                inCurrentQuarter(now.minusHours(4), now));
        visit(ananyaInfinia, VisitStatus.REDEEMED, null, "Eligible: seeded visit", -1, "LP-SEEDINFINIA0000001",
                now.minusHours(4));
        visit(ananyaInfinia, VisitStatus.REDEEMED, null, "Eligible: seeded visit", -1, "LP-SEEDINFINIA0000002",
                now.minusHours(5));
        visit(ananyaInfinia, VisitStatus.REDEEMED, null, "Eligible: seeded visit", -1, "LP-SEEDINFINIA0000003",
                now.minusHours(7));
        visit(rohanAce, VisitStatus.DENIED, DenialReason.INSUFFICIENT_SPEND,
                "Seeded: qualifying spend below threshold", 2, null, now.minusHours(2));
        visit(snehaDebit, VisitStatus.DENIED, DenialReason.INSUFFICIENT_SPEND,
                "Seeded: qualifying spend below threshold", 2, null, now.minusHours(3));
        visit(vikramDebit, VisitStatus.DENIED, DenialReason.EXPIRED_VOUCHER,
                "Seeded: voucher expired", 0, null, now.minusHours(5));
        visit(imranKotak, VisitStatus.DENIED, DenialReason.UNSUPPORTED_CARD,
                "Seeded: no programme for this card", 0, null, now.minusHours(6));
        visit(karanMillennia, VisitStatus.DENIED, DenialReason.QUOTA_EXCEEDED,
                "Seeded: quarterly quota used", 0, null, now.minusHours(1));
        loungeVisitRepository.save(LoungeVisit.builder()
                .cardMasked("411111******1111").customerId(999L).loungeId(LOUNGE)
                .attemptTime(now.minusHours(8)).attemptHour(now.minusHours(8).getHour())
                .status(VisitStatus.DENIED).denialReason(DenialReason.CARD_NOT_FOUND)
                .reasonMessage("Seeded: unknown card").visitsRemainingSnapshot(0).build());

        logScenarios(aaravAce, meeraAce, rohanAce, vikramDebit, snehaDebit, ananyaInfinia, karanMillennia,
                ishitaRegalia, divyaCoral, imranKotak);
    }

    // ------------------------------------------------------------------ builders

    private Customer customer(String name, String email, String mobile) {
        return customerRepository.save(Customer.builder().fullName(name).email(email).mobile(mobile).build());
    }

    private Card card(Customer customer, String pan, Bank bank, CardScheme scheme, CardType type,
                      String product, LocalDate issueDate) {
        return cardRepository.save(Card.builder()
                .customer(customer)
                .cardNumberHash(hasher.hash(pan))
                .bin(CardNumberUtil.bin(pan))
                .last4(CardNumberUtil.last4(pan))
                .bank(bank).scheme(scheme).cardType(type).productCode(product)
                .issueDate(issueDate)
                .expiryDate(LocalDate.now(clock).plusYears(3))
                .active(true)
                .build());
    }

    private void spend(Card card, LocalDate date, String amount, SpendCategory category, String merchant) {
        spends.add(SpendHistory.builder().card(card).transactionDate(date)
                .amount(new BigDecimal(amount)).category(category).merchant(merchant).build());
    }

    private void voucher(Card card, String code, LocalDateTime issuedAt, LocalDateTime expiresAt, boolean used) {
        voucherRepository.save(Voucher.builder().code(code).card(card).issuedAt(issuedAt).expiresAt(expiresAt)
                .used(used).usedAt(used ? issuedAt.plusHours(1) : null).build());
    }

    private void visit(Card card, VisitStatus status, DenialReason reason, String message, int remaining,
                       String token, LocalDateTime at) {
        loungeVisitRepository.save(LoungeVisit.builder()
                .card(card)
                .cardMasked("******" + card.getLast4())
                .customerId(card.getCustomer().getId())
                .bank(card.getBank())
                .productCode(card.getProductCode())
                .loungeId(LOUNGE)
                .attemptTime(at)
                .attemptHour(at.getHour())
                .status(status)
                .denialReason(reason)
                .reasonMessage(message)
                .passToken(token)
                .passExpiresAt(token == null ? null : at.plusHours(2))
                .redeemedAt(status == VisitStatus.REDEEMED ? at.plusMinutes(5) : null)
                .visitsRemainingSnapshot(remaining)
                .build());
    }

    /** Keeps seeded quota-consuming visits inside the current quarter even on the first day of a quarter. */
    private static LocalDateTime inCurrentQuarter(LocalDateTime candidate, LocalDateTime now) {
        LocalDateTime quarterStart = DateRangeUtil.currentQuarter(now.toLocalDate()).startOfDay();
        return candidate.isBefore(quarterStart) ? quarterStart : candidate;
    }

    private void logScenarios(Card... cards) {
        log.info("""

                ===================== DEMO DATA LOADED =====================
                Basic auth: reception / lounge@123   |   lounge: {}
                customerId  card number        scenario
                {}  {}  Axis ACE        - qualifies on spend (62,000)
                {}  {}  Axis ACE        - new card exception (waived)
                {}  {}  Axis ACE        - INSUFFICIENT_SPEND (fuel/cash/wallet/current month excluded)
                {}  {}  HDFC Debit      - eligible with voucher {} ; EXPIRED_VOUCHER with {}
                {}  {}  HDFC Debit      - INSUFFICIENT_SPEND (voucher {})
                {}  {}  HDFC Infinia    - unlimited access
                {}  {}  HDFC Millennia  - QUOTA_EXCEEDED
                {}  {}  HDFC Regalia    - eligible, 3 visits / quarter
                {}  {}  ICICI Coral     - generic strategy, 1 visit left
                {}  {}  Kotak debit     - UNSUPPORTED_CARD
                ============================================================""",
                LOUNGE,
                id(cards[0]), AXIS_ACE_QUALIFYING,
                id(cards[1]), AXIS_ACE_NEW_CARD,
                id(cards[2]), AXIS_ACE_LOW_SPEND,
                id(cards[3]), HDFC_DEBIT_QUALIFYING, VOUCHER_VALID, VOUCHER_EXPIRED,
                id(cards[4]), HDFC_DEBIT_LOW_SPEND, VOUCHER_LOW_SPEND_CARD,
                id(cards[5]), HDFC_INFINIA,
                id(cards[6]), HDFC_MILLENNIA_QUOTA_USED,
                id(cards[7]), HDFC_REGALIA_GOLD,
                id(cards[8]), ICICI_CORAL,
                id(cards[9]), KOTAK_UNSUPPORTED);
    }

    private static Long id(Card card) {
        return card.getCustomer().getId();
    }
}
