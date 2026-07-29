package com.carddemo.cbtrn02c.domain;

import java.math.BigDecimal;

public class TranCatBalRecord {
    public String acctId = "";
    public String typeCd = "";
    public String catCd = "";
    public BigDecimal tranCatBal = BigDecimal.ZERO;
    public String filler = "";

    public static TranCatBalRecord parse(String value) {
        FixedWidth.require(value, 50);
        TranCatBalRecord record = new TranCatBalRecord();
        record.acctId = value.substring(0, 11);
        record.typeCd = value.substring(11, 13);
        record.catCd = value.substring(13, 17);
        record.tranCatBal = FixedWidth.parseSignedNumber(value.substring(17, 28), 11);
        record.filler = value.substring(28, 50);
        return record;
    }

    public String format() {
        return FixedWidth.unsignedNumber(acctId, 11)
                + FixedWidth.text(typeCd, 2)
                + FixedWidth.unsignedNumber(catCd, 4)
                + FixedWidth.signedNumber(tranCatBal, 11)
                + FixedWidth.text(filler, 22);
    }

    public String key() {
        return acctId + typeCd + catCd;
    }
}
