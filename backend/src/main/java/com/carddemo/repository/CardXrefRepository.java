package com.carddemo.repository;

import com.carddemo.model.CardXref;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** B-0007 CXACAIX: READ on the alternate-index path by XREF-ACCT-ID (COACTVWC.cbl:727-735). */
public interface CardXrefRepository extends JpaRepository<CardXref, String> {

    /** Q-04: the legacy "first record on the AIX path" is made deterministic — lowest card number wins. */
    Optional<CardXref> findFirstByXrefAcctIdOrderByXrefCardNumberAsc(Long xrefAcctId);
}
