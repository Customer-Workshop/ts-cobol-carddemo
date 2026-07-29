package com.carddemo.cbtrn02c.domain;

import java.math.BigDecimal;

public class TranRecord {
    public String id = "";
    public String typeCd = "";
    public String catCd = "";
    public String source = "";
    public String desc = "";
    public BigDecimal amt = BigDecimal.ZERO;
    public String merchantId = "";
    public String merchantName = "";
    public String merchantCity = "";
    public String merchantZip = "";
    public String cardNum = "";
    public String origTs = "";
    public String procTs = "";
    public String filler = "";

    public static TranRecord parse(String value) {
        FixedWidth.require(value, 350);
        TranRecord record = new TranRecord();
        int offset = 0;
        record.id = value.substring(offset, offset + 16);
        offset += 16;
        record.typeCd = value.substring(offset, offset + 2);
        offset += 2;
        record.catCd = value.substring(offset, offset + 4);
        offset += 4;
        record.source = value.substring(offset, offset + 10);
        offset += 10;
        record.desc = value.substring(offset, offset + 100);
        offset += 100;
        record.amt = FixedWidth.parseSignedNumber(value.substring(offset, offset + 11), 11);
        offset += 11;
        record.merchantId = value.substring(offset, offset + 9);
        offset += 9;
        record.merchantName = value.substring(offset, offset + 50);
        offset += 50;
        record.merchantCity = value.substring(offset, offset + 50);
        offset += 50;
        record.merchantZip = value.substring(offset, offset + 10);
        offset += 10;
        record.cardNum = value.substring(offset, offset + 16);
        offset += 16;
        record.origTs = value.substring(offset, offset + 26);
        offset += 26;
        record.procTs = value.substring(offset, offset + 26);
        offset += 26;
        record.filler = value.substring(offset, offset + 20);
        return record;
    }

    public String format() {
        return FixedWidth.text(id, 16)
                + FixedWidth.text(typeCd, 2)
                + FixedWidth.unsignedNumber(catCd, 4)
                + FixedWidth.text(source, 10)
                + FixedWidth.text(desc, 100)
                + FixedWidth.signedNumber(amt, 11)
                + FixedWidth.unsignedNumber(merchantId, 9)
                + FixedWidth.text(merchantName, 50)
                + FixedWidth.text(merchantCity, 50)
                + FixedWidth.text(merchantZip, 10)
                + FixedWidth.text(cardNum, 16)
                + FixedWidth.text(origTs, 26)
                + FixedWidth.text(procTs, 26)
                + FixedWidth.text(filler, 20);
    }
}
