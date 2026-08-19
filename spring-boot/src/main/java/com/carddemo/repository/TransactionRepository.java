package com.carddemo.repository;
import com.carddemo.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findByTranCardNumberOrderByTranIdAsc(String tranCardNumber);
}
