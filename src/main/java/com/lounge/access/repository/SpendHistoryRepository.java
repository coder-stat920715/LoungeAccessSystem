package com.lounge.access.repository;

import com.lounge.access.dto.SpendCategory;
import com.lounge.access.entity.SpendHistory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpendHistoryRepository extends JpaRepository<SpendHistory, Long> {

    /** Sum of spend in [from, to] (inclusive) excluding the given categories; null when there is no spend. */
    @Query("select sum(s.amount) from SpendHistory s "
            + "where s.card.id = :cardId "
            + "and s.transactionDate between :from and :to "
            + "and s.category not in :excluded")
    BigDecimal sumQualifyingSpend(@Param("cardId") Long cardId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  @Param("excluded") Collection<SpendCategory> excluded);
}
