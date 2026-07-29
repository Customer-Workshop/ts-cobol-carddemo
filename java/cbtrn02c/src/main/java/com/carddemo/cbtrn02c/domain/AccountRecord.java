package com.carddemo.cbtrn02c.domain;

import java.math.BigDecimal;

public class AccountRecord {
    public String acctId = "";
    public String activeStatus = "";
    public BigDecimal currBal = BigDecimal.ZERO;
    public BigDecimal creditLimit = BigDecimal.ZERO;
    public BigDecimal cashCreditLimit = BigDecimal.ZERO;
    public String openDate = "";
    public String expirationDate = "";
    public String reissueDate = "";
    public BigDecimal currCycCredit = BigDecimal.ZERO;
    public BigDecimal currCycDebit = BigDecimal.ZERO;
    public String addrZip = "";
    public String groupId = "";
    public String filler = "";

    public static AccountRecord parse(String value) {
        FixedWidth.require(value, 300);
        AccountRecord record = new AccountRecord();
        int offset = 0;
        record.acctId = value.substring(offset, offset + 11);
        offset += 11;
        record.activeStatus = value.substring(offset, offset + 1);
        offset += 1;
        record.currBal = FixedWidth.parseSignedNumber(value.substring(offset, offset + 12), 12);
        offset += 12;
        record.creditLimit = FixedWidth.parseSignedNumber(value.substring(offset, offset + 12), 12);
        offset += 12;
        record.cashCreditLimit = FixedWidth.parseSignedNumber(value.substring(offset, offset + 12), 12);
        offset += 12;
        record.openDate = value.substring(offset, offset + 10);
        offset += 10;
        record.expirationDate = value.substring(offset, offset + 10);
        offset += 10;
        record.reissueDate = value.substring(offset, offset + 10);
        offset += 10;
        record.currCycCredit = FixedWidth.parseSignedNumber(value.substring(offset, offset + 12), 12);
        offset += 12;
        record.currCycDebit = FixedWidth.parseSignedNumber(value.substring(offset, offset + 12), 12);
        offset += 12;
        record.addrZip = value.substring(offset, offset + 10);
        offset += 10;
        record.groupId = value.substring(offset, offset + 10);
        offset += 10;
        record.filler = value.substring(offset, offset + 178);
        return record;
    }

    public String format() {
        return FixedWidth.unsignedNumber(acctId, 11)
                + FixedWidth.text(activeStatus, 1)
                + FixedWidth.signedNumber(currBal, 12)
                + FixedWidth.signedNumber(creditLimit, 12)
                + FixedWidth.signedNumber(cashCreditLimit, 12)
                + FixedWidth.text(openDate, 10)
                + FixedWidth.text(expirationDate, 10)
                + FixedWidth.text(reissueDate, 10)
                + FixedWidth.signedNumber(currCycCredit, 12)
                + FixedWidth.signedNumber(currCycDebit, 12)
                + FixedWidth.text(addrZip, 10)
                + FixedWidth.text(groupId, 10)
                + FixedWidth.text(filler, 178);
    }
}
