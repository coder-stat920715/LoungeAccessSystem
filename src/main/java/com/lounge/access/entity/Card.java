package com.lounge.access.entity;

import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardScheme;
import com.lounge.access.dto.CardType;
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
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The PAN is never stored: only an HMAC hash, the 8-digit BIN and the last four digits. */
@Entity
@Table(name = "cards", indexes = {
        @Index(name = "idx_card_customer_bin", columnList = "customer_id,bin")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, unique = true, length = 64)
    private String cardNumberHash;

    @Column(nullable = false, length = 8)
    private String bin;

    @Column(nullable = false, length = 4)
    private String last4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Bank bank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardScheme scheme;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CardType cardType;

    /** Product / tier, e.g. ACE, PLATINUM_DEBIT, MILLENNIA, REGALIA_GOLD, INFINIA, DINERS_BLACK. */
    @Column(nullable = false, length = 40)
    private String productCode;

    @Column(nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
