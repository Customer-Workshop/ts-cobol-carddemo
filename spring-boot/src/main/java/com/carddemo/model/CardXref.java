package com.carddemo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "card_xrefs")
public class CardXref {
    @Id private String xrefCardNumber;
    private Long xrefCustId;
    private Long xrefAcctId;

    public String getXrefCardNumber() { return xrefCardNumber; }
    public void setXrefCardNumber(String value) { xrefCardNumber = value; }
    public Long getXrefCustId() { return xrefCustId; }
    public void setXrefCustId(Long value) { xrefCustId = value; }
    public Long getXrefAcctId() { return xrefAcctId; }
    public void setXrefAcctId(Long value) { xrefAcctId = value; }
}
