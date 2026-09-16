package com.carddemo.repository;

import com.carddemo.model.PendingAuthDetail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * PAUTDTL1 access (S19-B3/S19-B8). Ascending order of the raw complement
 * key columns is newest-first real-time order — the GNP direction.
 */
public interface PendingAuthDetailRepository
        extends JpaRepository<PendingAuthDetail, PendingAuthDetail.Id> {

    // Unqualified GNP: first page (Pageable size N+1 for the look-ahead).
    List<PendingAuthDetail> findByIdAcctIdOrderByIdAuthDate9cAscIdAuthTime9cAsc(
            Long acctId, Pageable pageable);

    // Qualified GNP reposition + forward browse (PF8 path): rows strictly
    // after the supplied key.
    @Query("SELECT d FROM PendingAuthDetail d WHERE d.id.acctId = :acctId AND "
            + "(d.id.authDate9c > :date9c OR "
            + "(d.id.authDate9c = :date9c AND d.id.authTime9c > :time9c)) "
            + "ORDER BY d.id.authDate9c ASC, d.id.authTime9c ASC")
    List<PendingAuthDetail> findByAcctIdAfterKey(
            @Param("acctId") Long acctId,
            @Param("date9c") Integer date9c,
            @Param("time9c") Integer time9c,
            Pageable pageable);

    // REST 'dir=bwd' page: the rows immediately before the cursor, fetched
    // in descending order (caller reverses to restore newest-first).
    @Query("SELECT d FROM PendingAuthDetail d WHERE d.id.acctId = :acctId AND "
            + "(d.id.authDate9c < :date9c OR "
            + "(d.id.authDate9c = :date9c AND d.id.authTime9c < :time9c)) "
            + "ORDER BY d.id.authDate9c DESC, d.id.authTime9c DESC")
    List<PendingAuthDetail> findByAcctIdBeforeKey(
            @Param("acctId") Long acctId,
            @Param("date9c") Integer date9c,
            @Param("time9c") Integer time9c,
            Pageable pageable);

    // Qualified GNP at a page-start key (PF7 path): inclusive read.
    @Query("SELECT d FROM PendingAuthDetail d WHERE d.id.acctId = :acctId AND "
            + "(d.id.authDate9c > :date9c OR "
            + "(d.id.authDate9c = :date9c AND d.id.authTime9c >= :time9c)) "
            + "ORDER BY d.id.authDate9c ASC, d.id.authTime9c ASC")
    List<PendingAuthDetail> findByAcctIdFromKey(
            @Param("acctId") Long acctId,
            @Param("date9c") Integer date9c,
            @Param("time9c") Integer time9c,
            Pageable pageable);
}
