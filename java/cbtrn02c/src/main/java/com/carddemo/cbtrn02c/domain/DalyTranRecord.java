package com.carddemo.cbtrn02c.domain;
public class DalyTranRecord extends TranRecord {
  public static DalyTranRecord parse(String s){ TranRecord x=TranRecord.parse(s); DalyTranRecord r=new DalyTranRecord(); r.id=x.id;r.typeCd=x.typeCd;r.catCd=x.catCd;r.source=x.source;r.desc=x.desc;r.amt=x.amt;r.merchantId=x.merchantId;r.merchantName=x.merchantName;r.merchantCity=x.merchantCity;r.merchantZip=x.merchantZip;r.cardNum=x.cardNum;r.origTs=x.origTs;r.procTs=x.procTs;r.filler=x.filler;return r; }
}
