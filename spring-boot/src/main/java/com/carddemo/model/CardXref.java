package com.carddemo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "card_xrefs")
public class CardXref {
    @Id @Column(length = 16, nullable = false) private String xrefCardNumber;
    private Long xrefCustId;
    private Long xrefAcctId;

    public String getXrefCardNumber() { return xrefCardNumber; }
    public void setXrefCardNumber(String value) { xrefCardNumber = value; }
    public Long getXrefCustId() { return xrefCustId; }
    public void setXrefCustId(Long value) { xrefCustId = value; }
    public Long getXrefAcctId() { return xrefAcctId; }
    public void setXrefAcctId(Long value) { xrefAcctId = value; }
}
