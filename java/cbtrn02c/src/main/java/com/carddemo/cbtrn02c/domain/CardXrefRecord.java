package com.carddemo.cbtrn02c.domain;

public class CardXrefRecord {
    public String cardNum = "";
    public String custId = "";
    public String acctId = "";
    public String filler = "";

    public static CardXrefRecord parse(String value) {
        FixedWidth.require(value, 50);
        CardXrefRecord record = new CardXrefRecord();
        record.cardNum = value.substring(0, 16);
        record.custId = value.substring(16, 25);
        record.acctId = value.substring(25, 36);
        record.filler = value.substring(36, 50);
        return record;
    }

    public String format() {
        return FixedWidth.text(cardNum, 16)
                + FixedWidth.unsignedNumber(custId, 9)
                + FixedWidth.unsignedNumber(acctId, 11)
                + FixedWidth.text(filler, 14);
    }
}
