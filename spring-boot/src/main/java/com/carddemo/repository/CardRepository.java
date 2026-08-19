package com.carddemo.repository;
import com.carddemo.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CardRepository extends JpaRepository<Card, String> {
    List<Card> findByCardAcctId(Long cardAcctId);
}
