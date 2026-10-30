package com.lounge.access.repository;

import com.lounge.access.entity.Card;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRepository extends JpaRepository<Card, Long> {

    /** Row lock serialises concurrent entries on one card so quotas cannot be over-consumed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Card c where c.cardNumberHash = :hash")
    Optional<Card> findByCardNumberHashForUpdate(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Card c where c.customer.id = :customerId and c.bin like concat(:bin, '%')")
    List<Card> findByCustomerIdAndBinPrefixForUpdate(@Param("customerId") Long customerId,
                                                     @Param("bin") String bin);
}
