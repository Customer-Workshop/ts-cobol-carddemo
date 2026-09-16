package com.carddemo.repository;
import com.carddemo.model.Customer;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    // READ UPDATE on CUSTDAT — FOR UPDATE NOWAIT (D6).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT c FROM Customer c WHERE c.custId = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") long id);
}
