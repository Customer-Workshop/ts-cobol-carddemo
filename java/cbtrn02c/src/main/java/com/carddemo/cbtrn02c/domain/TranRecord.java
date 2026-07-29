package com.carddemo.cbtrn02c.domain;
import java.math.BigDecimal;
public class TranRecord {
  public String id="", typeCd="", catCd="", source="", desc="", merchantId="", merchantName="", merchantCity="", merchantZip="", cardNum="", origTs="", procTs="", filler="";
  public BigDecimal amt=BigDecimal.ZERO;
  public static TranRecord parse(String s){ FixedWidth.require(s,350); TranRecord r=new TranRecord(); int p=0; r.id=s.substring(p,p+=16);r.typeCd=s.substring(p,p+=2);r.catCd=s.substring(p,p+=4);r.source=s.substring(p,p+=10);r.desc=s.substring(p,p+=100);r.amt=FixedWidth.parseSigned(s.substring(p,p+=11),11);r.merchantId=s.substring(p,p+=9);r.merchantName=s.substring(p,p+=50);r.merchantCity=s.substring(p,p+=50);r.merchantZip=s.substring(p,p+=10);r.cardNum=s.substring(p,p+=16);r.origTs=s.substring(p,p+=26);r.procTs=s.substring(p,p+=26);r.filler=s.substring(p,p+20);return r;}
  public String format(){return FixedWidth.text(id,16)+FixedWidth.text(typeCd,2)+FixedWidth.num(catCd,4)+FixedWidth.text(source,10)+FixedWidth.text(desc,100)+FixedWidth.signed(amt,11)+FixedWidth.num(merchantId,9)+FixedWidth.text(merchantName,50)+FixedWidth.text(merchantCity,50)+FixedWidth.text(merchantZip,10)+FixedWidth.text(cardNum,16)+FixedWidth.text(origTs,26)+FixedWidth.text(procTs,26)+FixedWidth.text(filler,20);}
}
