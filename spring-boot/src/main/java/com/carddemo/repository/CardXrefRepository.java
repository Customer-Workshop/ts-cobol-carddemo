package com.carddemo.repository;
import com.carddemo.model.CardXref;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface CardXrefRepository extends JpaRepository<CardXref, String> {
    // CXACAIX is keyed on card number, so its alternate-index read returns
    // the lowest-card-number record first (FR-S02-10).
    @Query("SELECT x FROM CardXref x WHERE x.xrefAcctId = :xrefAcctId ORDER BY x.xrefCardNumber")
    List<CardXref> findByXrefAcctId(Long xrefAcctId);
}
