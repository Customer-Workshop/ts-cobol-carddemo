package com.carddemo.cbtrn02c.domain;
public class CardXrefRecord {
 public String cardNum="",custId="",acctId="",filler="";
 public static CardXrefRecord parse(String s){FixedWidth.require(s,50);CardXrefRecord r=new CardXrefRecord();r.cardNum=s.substring(0,16);r.custId=s.substring(16,25);r.acctId=s.substring(25,36);r.filler=s.substring(36);return r;}
 public String format(){return FixedWidth.text(cardNum,16)+FixedWidth.num(custId,9)+FixedWidth.num(acctId,11)+FixedWidth.text(filler,14);}
}
