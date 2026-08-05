package com.carddemo.data.repository;

import com.carddemo.data.entity.TransactionCategory;
import com.carddemo.data.entity.TransactionCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionCategoryRepository
    extends JpaRepository<TransactionCategory, TransactionCategoryId> {}
