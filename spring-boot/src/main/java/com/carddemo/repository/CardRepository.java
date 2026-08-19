package com.carddemo.repository;
import com.carddemo.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface CardRepository extends JpaRepository<Card, String> {
    List<Card> findByCardAcctId(Long cardAcctId);
    Page<Card> findByCardAcctId(Long cardAcctId, Pageable pageable);
    Page<Card> findByCardAcctIdAndCardNumberGreaterThanEqual(
            Long cardAcctId, String cardNumber, Pageable pageable);
    Page<Card> findByCardAcctIdAndCardNumberLessThanEqual(
            Long cardAcctId, String cardNumber, Pageable pageable);
    Page<Card> findByCardNumberGreaterThanEqual(String cardNumber, Pageable pageable);
    Page<Card> findByCardNumberLessThanEqual(String cardNumber, Pageable pageable);
}
