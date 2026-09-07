package com.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** CARD-XREF-RECORD, app/cpy/CVACT03Y.cpy:4-8 (VSAM CCXREF base cluster; CXACAIX path = index, B-0007). */
@Entity
@Table(name = "card_xrefs")
public class CardXref {

    @Id
    @Column(name = "xref_card_number", length = 16, nullable = false)
    private String xrefCardNumber;

    @Column(name = "xref_cust_id")
    private Long xrefCustId;

    @Column(name = "xref_acct_id")
    private Long xrefAcctId;

    public String getXrefCardNumber() {
        return xrefCardNumber;
    }

    public void setXrefCardNumber(String xrefCardNumber) {
        this.xrefCardNumber = xrefCardNumber;
    }

    public Long getXrefCustId() {
        return xrefCustId;
    }

    public void setXrefCustId(Long xrefCustId) {
        this.xrefCustId = xrefCustId;
    }

    public Long getXrefAcctId() {
        return xrefAcctId;
    }

    public void setXrefAcctId(Long xrefAcctId) {
        this.xrefAcctId = xrefAcctId;
    }
}
