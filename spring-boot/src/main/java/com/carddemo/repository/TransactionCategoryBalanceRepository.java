package com.carddemo.repository;
import com.carddemo.model.TransactionCategoryBalance;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TransactionCategoryBalanceRepository extends JpaRepository<TransactionCategoryBalance, TransactionCategoryBalance.Id> {}
