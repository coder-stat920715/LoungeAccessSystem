package com.lounge.access.repository;

import com.lounge.access.entity.LoungeVisit;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoungeVisitRepository extends JpaRepository<LoungeVisit, Long> {

    /**
     * Visits that consume quota in [from, to): redeemed visits plus issued passes that have not yet expired.
     * Unredeemed passes that expired release their quota automatically.
     */
    @Query("select count(v) from LoungeVisit v where v.card.id = :cardId "
            + "and v.attemptTime >= :from and v.attemptTime < :to "
            + "and (v.status = com.lounge.access.dto.VisitStatus.REDEEMED "
            + "  or (v.status = com.lounge.access.dto.VisitStatus.APPROVED and v.passExpiresAt > :now))")
    long countConsumedVisits(@Param("cardId") Long cardId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to,
                             @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from LoungeVisit v where v.passToken = :token")
    Optional<LoungeVisit> findByPassTokenForUpdate(@Param("token") String token);

    @Query("select v from LoungeVisit v where v.card.id = :cardId and v.loungeId = :loungeId "
            + "and v.status = com.lounge.access.dto.VisitStatus.APPROVED and v.passExpiresAt > :now "
            + "order by v.attemptTime desc")
    List<LoungeVisit> findActivePasses(@Param("cardId") Long cardId,
                                       @Param("loungeId") String loungeId,
                                       @Param("now") LocalDateTime now);

    // ------------------------------ analytics ------------------------------

    @Query("select v.status, count(v) from LoungeVisit v where v.attemptTime >= :since group by v.status")
    List<Object[]> countByStatusSince(@Param("since") LocalDateTime since);

    @Query("select v.denialReason, count(v) from LoungeVisit v "
            + "where v.attemptTime >= :since and v.status = com.lounge.access.dto.VisitStatus.DENIED "
            + "group by v.denialReason")
    List<Object[]> countDenialsByReasonSince(@Param("since") LocalDateTime since);

    @Query("select v.bank, v.status, count(v) from LoungeVisit v where v.attemptTime >= :since "
            + "group by v.bank, v.status")
    List<Object[]> countByBankAndStatusSince(@Param("since") LocalDateTime since);

    @Query("select v.bank, v.productCode, count(v) from LoungeVisit v where v.attemptTime >= :since "
            + "group by v.bank, v.productCode order by count(v) desc")
    List<Object[]> countByBankAndProductSince(@Param("since") LocalDateTime since);

    @Query("select v.attemptHour, count(v) from LoungeVisit v where v.attemptTime >= :since "
            + "group by v.attemptHour")
    List<Object[]> countByHourSince(@Param("since") LocalDateTime since);
}
