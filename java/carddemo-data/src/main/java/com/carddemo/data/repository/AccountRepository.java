package com.carddemo.data.repository;

import com.carddemo.data.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
  Account findByAcctId(Long acctId);
}
