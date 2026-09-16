package com.carddemo.repository;

import com.carddemo.model.PendingAuthSummary;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * PAUTSUM0 root access (S19-B3): {@code findById} is the IMS GU on
 * PA-ACCT-ID.
 */
public interface PendingAuthSummaryRepository extends JpaRepository<PendingAuthSummary, Long> {
}
