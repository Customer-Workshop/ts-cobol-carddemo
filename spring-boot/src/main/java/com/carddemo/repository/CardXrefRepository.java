package com.carddemo.repository;
import com.carddemo.model.CardXref;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CardXrefRepository extends JpaRepository<CardXref, String> {
    List<CardXref> findByXrefAcctId(Long xrefAcctId);
}
