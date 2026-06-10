package com.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity mapped from COBOL copybook CVACT02Y.cpy (CARD-RECORD, RECLN 150).
 */
@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    /** CARD-NUM PIC X(16) */
    @Id
    @Column(name = "card_num", length = 16)
    private String cardNum;

    /** CARD-ACCT-ID PIC 9(11) */
    @Column(name = "card_acct_id")
    private Long cardAcctId;

    /** CARD-CVV-CD PIC 9(03) */
    @Column(name = "card_cvv_cd")
    private Integer cardCvvCd;

    /** CARD-EMBOSSED-NAME PIC X(50) */
    @Column(name = "card_embossed_name", length = 50)
    private String cardEmbossedName;

    /** CARD-EXPIRAION-DATE PIC X(10) */
    @Column(name = "card_expiration_date", length = 10)
    private String cardExpirationDate;

    /** CARD-ACTIVE-STATUS PIC X(01) */
    @Column(name = "card_active_status", length = 1)
    private String cardActiveStatus;
}
