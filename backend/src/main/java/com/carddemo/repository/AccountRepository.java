package com.carddemo.repository;

import com.carddemo.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

/** B-0001 ACCTDAT: READ by ACCT-ID (COACTVWC.cbl:776-784) -> findById. */
public interface AccountRepository extends JpaRepository<Account, Long> {
}
