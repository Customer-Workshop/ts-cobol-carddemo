package com.carddemo.repository;
import com.carddemo.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findByTranCardNumberOrderByTranIdAsc(String tranCardNumber);
    Page<Transaction> findByTranIdGreaterThanEqual(String tranId, Pageable pageable);
    Page<Transaction> findByTranIdLessThanEqual(String tranId, Pageable pageable);
    Transaction findTopByOrderByTranIdDesc();
    List<Transaction> findByTranProcessTimestampBetweenOrderByTranProcessTimestampAscTranIdAsc(
            LocalDateTime start, LocalDateTime end);
}
