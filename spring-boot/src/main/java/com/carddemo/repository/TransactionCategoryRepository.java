package com.carddemo.repository;

import com.carddemo.model.TransactionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionCategoryRepository
        extends JpaRepository<TransactionCategory, TransactionCategory.Id> {

    // S21-B4 child-guard probe for the -532 delete path.
    long countByIdTranTypeCode(String tranTypeCode);

    // TRANEXTR TRANCATG.PS ordering (jcl: ORDER BY TRC_TYPE_CODE, TRC_TYPE_CATEGORY).
    List<TransactionCategory>
        findAllByOrderByIdTranTypeCodeAscIdTranCategoryCodeAsc();
}
