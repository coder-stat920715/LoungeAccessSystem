package com.lounge.access.service.impl;

import com.lounge.access.config.LoungeProperties;
import com.lounge.access.dto.DenialReason;
import com.lounge.access.dto.EligibilityRequest;
import com.lounge.access.dto.EligibilityResponse;
import com.lounge.access.dto.RedeemPassRequest;
import com.lounge.access.dto.RedeemPassResponse;
import com.lounge.access.dto.VisitStatus;
import com.lounge.access.entity.Card;
import com.lounge.access.entity.LoungeVisit;
import com.lounge.access.entity.Voucher;
import com.lounge.access.exception.PassAlreadyRedeemedException;
import com.lounge.access.exception.PassExpiredException;
import com.lounge.access.exception.PassLoungeMismatchException;
import com.lounge.access.exception.PassNotFoundException;
import com.lounge.access.mapper.LoungeVisitMapper;
import com.lounge.access.repository.CardRepository;
import com.lounge.access.repository.LoungeVisitRepository;
import com.lounge.access.repository.VoucherRepository;
import com.lounge.access.service.engine.EligibilityContext;
import com.lounge.access.service.engine.EligibilityDecision;
import com.lounge.access.service.engine.LoungeEligibilityEngine;
import com.lounge.access.util.CardNumberHasher;
import com.lounge.access.util.CardNumberUtil;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoungeAccessService {

    private final CardRepository cardRepository;
    private final VoucherRepository voucherRepository;
    private final LoungeVisitRepository loungeVisitRepository;
    private final LoungeEligibilityEngine engine;
    private final LoungeVisitMapper mapper;
    private final CardNumberHasher cardNumberHasher;
    private final LoungeProperties properties;
    private final Clock clock;

    private record CardResolution(Card card, String failureMessage) {
    }

    /**
     * Evaluates eligibility, records the attempt (approved or denied) and, on approval, consumes the quota /
     * voucher and issues a lounge pass. Denials are business outcomes and therefore do not roll back the audit row.
     */
    @Transactional
    public EligibilityResponse checkEligibility(EligibilityRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        String digits = CardNumberUtil.normalize(request.cardNumber());
        String masked = CardNumberUtil.mask(digits);
        String loungeId = request.loungeId().trim().toUpperCase(Locale.ROOT);

        CardResolution resolution = resolveCard(request.customerId(), digits);
        if (resolution.card() == null) {
            return recordDenial(null, request, masked, loungeId, DenialReason.CARD_NOT_FOUND,
                    resolution.failureMessage(), now);
        }
        Card card = resolution.card();

        if (!card.getCustomer().getId().equals(request.customerId())) {
            return recordDenial(card, request, masked, loungeId, DenialReason.CUSTOMER_MISMATCH,
                    "Card does not belong to the specified customer", now);
        }

        // Idempotency: re-presenting a card that already holds a live pass for this lounge returns that pass
        // instead of burning a second visit.
        List<LoungeVisit> activePasses = loungeVisitRepository.findActivePasses(card.getId(), loungeId, now);
        if (!activePasses.isEmpty()) {
            log.info("Re-issuing active pass for card={} lounge={}", masked, loungeId);
            return mapper.toEligibilityResponse(activePasses.get(0));
        }

        EligibilityDecision decision = engine.evaluate(
                new EligibilityContext(card, loungeId, request.voucherCode(), now));

        if (!decision.eligible()) {
            return recordDenial(card, request, masked, loungeId, decision.denialReason(),
                    decision.message(), decision.visitsRemaining(), now);
        }

        LoungeVisit visit = newVisit(card, request, masked, loungeId, now);
        visit.setStatus(VisitStatus.APPROVED);
        visit.setReasonMessage(decision.message());
        visit.setVisitsRemainingSnapshot(decision.visitsRemaining());
        visit.setPassToken(generatePassToken());
        visit.setPassExpiresAt(now.plusMinutes(properties.getPass().getValidityMinutes()));

        Voucher voucher = decision.voucherToConsume();
        if (voucher != null) {
            voucher.setUsed(true);
            voucher.setUsedAt(now);
            voucherRepository.save(voucher);
            visit.setVoucherCode(voucher.getCode());
        }
        LoungeVisit saved = loungeVisitRepository.save(visit);
        log.info("Lounge access APPROVED card={} lounge={} remaining={}", masked, loungeId,
                decision.visitsRemaining());
        return mapper.toEligibilityResponse(saved);
    }

    /** Marks an issued pass as used at the reception terminal. */
    @Transactional
    public RedeemPassResponse redeemPass(RedeemPassRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        LoungeVisit visit = loungeVisitRepository.findByPassTokenForUpdate(request.passToken().trim())
                .orElseThrow(PassNotFoundException::new);

        if (!visit.getLoungeId().equalsIgnoreCase(request.loungeId().trim())) {
            throw new PassLoungeMismatchException();
        }
        if (visit.getStatus() == VisitStatus.REDEEMED) {
            throw new PassAlreadyRedeemedException(visit.getRedeemedAt());
        }
        if (visit.getStatus() != VisitStatus.APPROVED) {
            throw new PassNotFoundException();
        }
        if (!visit.getPassExpiresAt().isAfter(now)) {
            throw new PassExpiredException(visit.getPassExpiresAt());
        }

        visit.setStatus(VisitStatus.REDEEMED);
        visit.setRedeemedAt(now);
        LoungeVisit saved = loungeVisitRepository.save(visit);
        log.info("Pass redeemed visit={} lounge={} terminal={}", saved.getId(), saved.getLoungeId(),
                request.terminalId());
        return mapper.toRedeemPassResponse(saved);
    }

    // ------------------------------------------------------------------ helpers

    private CardResolution resolveCard(Long customerId, String digits) {
        if (CardNumberUtil.isFullNumber(digits)) {
            return cardRepository.findByCardNumberHashForUpdate(cardNumberHasher.hash(digits))
                    .map(c -> new CardResolution(c, null))
                    .orElseGet(() -> new CardResolution(null, "No card matches the presented card number"));
        }
        if (CardNumberUtil.isBin(digits)) {
            List<Card> matches = cardRepository.findByCustomerIdAndBinPrefixForUpdate(customerId, digits);
            if (matches.size() == 1) {
                return new CardResolution(matches.get(0), null);
            }
            return new CardResolution(null, matches.isEmpty()
                    ? "No card of this customer matches the presented BIN"
                    : "BIN matches multiple cards; present the full card number");
        }
        return new CardResolution(null, "Present a full card number or a 6-8 digit BIN");
    }

    private EligibilityResponse recordDenial(Card card, EligibilityRequest request, String masked, String loungeId,
                                             DenialReason reason, String message, LocalDateTime now) {
        return recordDenial(card, request, masked, loungeId, reason, message, 0, now);
    }

    private EligibilityResponse recordDenial(Card card, EligibilityRequest request, String masked, String loungeId,
                                             DenialReason reason, String message, int visitsRemaining,
                                             LocalDateTime now) {
        LoungeVisit visit = newVisit(card, request, masked, loungeId, now);
        visit.setStatus(VisitStatus.DENIED);
        visit.setDenialReason(reason);
        visit.setReasonMessage(message);
        visit.setVisitsRemainingSnapshot(visitsRemaining);
        LoungeVisit saved = loungeVisitRepository.save(visit);
        log.info("Lounge access DENIED card={} lounge={} reason={}", masked, loungeId, reason);
        return mapper.toEligibilityResponse(saved);
    }

    private LoungeVisit newVisit(Card card, EligibilityRequest request, String masked, String loungeId,
                                 LocalDateTime now) {
        String voucher = request.voucherCode() == null || request.voucherCode().isBlank()
                ? null : request.voucherCode().trim();
        return LoungeVisit.builder()
                .card(card)
                .cardMasked(masked)
                .customerId(request.customerId())
                .bank(card == null ? null : card.getBank())
                .productCode(card == null ? null : card.getProductCode())
                .loungeId(loungeId)
                .attemptTime(now)
                .attemptHour(now.getHour())
                .voucherCode(voucher)
                .visitsRemainingSnapshot(0)
                .build();
    }

    private static String generatePassToken() {
        return "LP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }
}
