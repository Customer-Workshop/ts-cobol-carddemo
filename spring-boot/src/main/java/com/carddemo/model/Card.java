package com.carddemo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDate;

@Entity
@Table(name = "cards")
public class Card {
    @Id @Column(length = 16, nullable = false) private String cardNumber;
    private Long cardAcctId;
    private Integer cardCvvCode;
    @Column(length = 50) private String cardEmbossedName;
    private LocalDate cardExpirationDate;
    @Column(length = 1) private String cardActiveStatus;

    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String value) { cardNumber = value; }
    public Long getCardAcctId() { return cardAcctId; }
    public void setCardAcctId(Long value) { cardAcctId = value; }
    public Integer getCardCvvCode() { return cardCvvCode; }
    public void setCardCvvCode(Integer value) { cardCvvCode = value; }
    public String getCardEmbossedName() { return cardEmbossedName; }
    public void setCardEmbossedName(String value) { cardEmbossedName = value; }
    public LocalDate getCardExpirationDate() { return cardExpirationDate; }
    public void setCardExpirationDate(LocalDate value) { cardExpirationDate = value; }
    public String getCardActiveStatus() { return cardActiveStatus; }
    public void setCardActiveStatus(String value) { cardActiveStatus = value; }
}
