package com.carddemo.repository;

import com.carddemo.model.TransactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionTypeRepository extends JpaRepository<TransactionType, String> {

    // COTRTLIC cursor C-TR-TYPE-FORWARD (:339-368): TR_TYPE >= start with the
    // optional type-equality and TRIM'd description LIKE filters.
    @Query("select t from TransactionType t"
            + " where (:typeCode is null or t.tranType = :typeCode)"
            + " and (:descPattern is null or t.description like :descPattern)"
            + " and t.tranType >= :start order by t.tranType")
    List<TransactionType> pageForward(@Param("typeCode") String typeCode,
            @Param("descPattern") String descPattern,
            @Param("start") String start, Pageable pageable);

    // Cursor C-TR-TYPE-BACKWARD: same filter set walking back from the anchor.
    @Query("select t from TransactionType t"
            + " where (:typeCode is null or t.tranType = :typeCode)"
            + " and (:descPattern is null or t.description like :descPattern)"
            + " and t.tranType < :start order by t.tranType desc")
    List<TransactionType> pageBackward(@Param("typeCode") String typeCode,
            @Param("descPattern") String descPattern,
            @Param("start") String start, Pageable pageable);

    // 9100-CHECK-FILTERS (:1801-1819): count under the same predicates.
    @Query("select count(t) from TransactionType t"
            + " where (:typeCode is null or t.tranType = :typeCode)"
            + " and (:descPattern is null or t.description like :descPattern)")
    long countFiltered(@Param("typeCode") String typeCode,
            @Param("descPattern") String descPattern);

    List<TransactionType> findAllByOrderByTranType();
}
