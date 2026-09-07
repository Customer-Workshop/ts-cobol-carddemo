package com.carddemo.repository;
import com.carddemo.model.TransactionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TransactionCategoryRepository extends JpaRepository<TransactionCategory, TransactionCategory.Id> {}
