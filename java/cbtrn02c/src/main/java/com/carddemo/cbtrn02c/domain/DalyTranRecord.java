package com.carddemo.cbtrn02c.domain;

public class DalyTranRecord extends TranRecord {
    public static DalyTranRecord parse(String value) {
        TranRecord parsed = TranRecord.parse(value);
        DalyTranRecord record = new DalyTranRecord();
        record.id = parsed.id;
        record.typeCd = parsed.typeCd;
        record.catCd = parsed.catCd;
        record.source = parsed.source;
        record.desc = parsed.desc;
        record.amt = parsed.amt;
        record.merchantId = parsed.merchantId;
        record.merchantName = parsed.merchantName;
        record.merchantCity = parsed.merchantCity;
        record.merchantZip = parsed.merchantZip;
        record.cardNum = parsed.cardNum;
        record.origTs = parsed.origTs;
        record.procTs = parsed.procTs;
        record.filler = parsed.filler;
        return record;
    }
}
