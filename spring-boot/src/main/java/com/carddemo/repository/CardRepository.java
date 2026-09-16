package com.carddemo.repository;
import com.carddemo.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // COCRDLIC keyed browse verbs (S-04): STARTBR GTEQ + READNEXT reads the
    // key-ordered file from the browse key, applying the account/card equality
    // filters of 9500-FILTER-RECORDS (COCRDLIC.cbl:1382-1411). READPREV walks
    // backward from the anchor. The unfiltered look-ahead mirrors the raw
    // READNEXT after the 7th matching row (:1197-1214).
    @Query("select c from Card c where c.cardNumber >= :startKey "
            + "and (:acctId is null or c.cardAcctId = :acctId) "
            + "and (:cardNumber is null or c.cardNumber = :cardNumber) "
            + "order by c.cardNumber asc")
    List<Card> browseForward(@Param("startKey") String startKey,
                             @Param("acctId") Long acctId,
                             @Param("cardNumber") String cardNumber,
                             Pageable pageable);

    @Query("select c from Card c where c.cardNumber < :anchor "
            + "and (:acctId is null or c.cardAcctId = :acctId) "
            + "and (:cardNumber is null or c.cardNumber = :cardNumber) "
            + "order by c.cardNumber desc")
    List<Card> browseBackward(@Param("anchor") String anchor,
                              @Param("acctId") Long acctId,
                              @Param("cardNumber") String cardNumber,
                              Pageable pageable);

    @Query("select c from Card c where c.cardNumber > :cardNumber "
            + "order by c.cardNumber asc")
    List<Card> nextAfter(@Param("cardNumber") String cardNumber, Pageable pageable);
}
