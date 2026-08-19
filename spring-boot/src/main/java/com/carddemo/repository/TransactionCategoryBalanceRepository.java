package com.carddemo.repository;
import com.carddemo.model.TransactionCategoryBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface TransactionCategoryBalanceRepository extends JpaRepository<TransactionCategoryBalance, TransactionCategoryBalance.Id> {
    Page<TransactionCategoryBalance> findAll(Pageable pageable);
}
