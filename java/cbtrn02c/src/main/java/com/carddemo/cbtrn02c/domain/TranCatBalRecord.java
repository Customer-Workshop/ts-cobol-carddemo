package com.carddemo.cbtrn02c.domain;
import java.math.BigDecimal;
public class TranCatBalRecord {
 public String acctId="",typeCd="",catCd="",filler=""; public BigDecimal tranCatBal=BigDecimal.ZERO;
 public static TranCatBalRecord parse(String s){FixedWidth.require(s,50);TranCatBalRecord r=new TranCatBalRecord();r.acctId=s.substring(0,11);r.typeCd=s.substring(11,13);r.catCd=s.substring(13,17);r.tranCatBal=FixedWidth.parseSigned(s.substring(17,28),11);r.filler=s.substring(28);return r;}
 public String format(){return FixedWidth.num(acctId,11)+FixedWidth.text(typeCd,2)+FixedWidth.num(catCd,4)+FixedWidth.signed(tranCatBal,11)+FixedWidth.text(filler,22);}
 public String key(){return acctId+typeCd+catCd;}
}
