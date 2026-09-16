package com.carddemo.repository;
import com.carddemo.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByAcctGroupId(String acctGroupId);
}
