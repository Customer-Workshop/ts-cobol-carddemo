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
 * JPA entity mapped from COBOL copybook CVACT03Y.cpy (CARD-XREF-RECORD, RECLN 50).
 */
@Entity
@Table(name = "card_xrefs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardXref {

    /** XREF-CARD-NUM PIC X(16) */
    @Id
    @Column(name = "xref_card_num", length = 16)
    private String xrefCardNum;

    /** XREF-CUST-ID PIC 9(09) */
    @Column(name = "xref_cust_id")
    private Long xrefCustId;

    /** XREF-ACCT-ID PIC 9(11) */
    @Column(name = "xref_acct_id")
    private Long xrefAcctId;
}
