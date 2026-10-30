package com.lounge.access.entity;

import com.lounge.access.dto.Bank;
import com.lounge.access.dto.DenialReason;
import com.lounge.access.dto.VisitStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Audit record of every lounge access attempt (approved or denied). Bank/product are denormalised for analytics. */
@Entity
@Table(name = "lounge_visits", indexes = {
        @Index(name = "idx_visit_card_status", columnList = "card_id,status"),
        @Index(name = "idx_visit_attempt_time", columnList = "attempt_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoungeVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null when the presented card could not be resolved (CARD_NOT_FOUND). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private Card card;

    @Column(nullable = false, length = 32)
    private String cardMasked;

    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Bank bank;

    @Column(length = 40)
    private String productCode;

    @Column(nullable = false, length = 32)
    private String loungeId;

    @Column(nullable = false)
    private LocalDateTime attemptTime;

    @Column(nullable = false)
    private int attemptHour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VisitStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DenialReason denialReason;

    @Column(length = 500)
    private String reasonMessage;

    @Column(unique = true, length = 40)
    private String passToken;

    private LocalDateTime passExpiresAt;

    @Column(length = 32)
    private String voucherCode;

    private LocalDateTime redeemedAt;

    @Column(nullable = false)
    private int visitsRemainingSnapshot;
}
