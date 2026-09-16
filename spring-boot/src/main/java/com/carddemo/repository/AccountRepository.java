package com.carddemo.repository;
import com.carddemo.model.Account;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByAcctGroupId(String acctGroupId);

    // READ UPDATE on ACCTDAT — FOR UPDATE NOWAIT (D6).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT a FROM Account a WHERE a.acctId = :id")
    Optional<Account> findByIdForUpdate(@Param("id") long id);

    // ACCTDAT READ UPDATE (S11-B2): the CICS record lock becomes SELECT ...
    // FOR UPDATE inside the service transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.acctId = :acctId")
    Optional<Account> findForUpdate(Long acctId);
}
