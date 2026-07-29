package com.carddemo.cbtrn02c.service;
import com.carddemo.cbtrn02c.domain.*;import com.carddemo.cbtrn02c.repo.BatchFiles;import java.math.*;import java.time.*;import java.time.format.*;import java.util.*;
public class TransactionPosterService {
 public record Result(int processed,int rejected,int exitCode){}
 private static final DateTimeFormatter TS=DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS").withZone(ZoneId.systemDefault());
 public Result run(BatchFiles f){int processed=0,rejected=0;for(DalyTranRecord d:f.daily){processed++;CardXrefRecord x=f.xref.get(d.cardNum);int reason=0;String desc="";
   AccountRecord a=null;if(x==null){reason=100;desc="INVALID CARD NUMBER FOUND";}else if((a=f.accounts.get(x.acctId))==null){reason=101;desc="ACCOUNT RECORD NOT FOUND";}else{BigDecimal temp=a.currCycCredit.subtract(a.currCycDebit).add(d.amt);if(a.creditLimit.compareTo(temp)<0){reason=102;desc="OVERLIMIT TRANSACTION";}if(a.expirationDate.compareTo(d.origTs.substring(0,10))<0){reason=103;desc="TRANSACTION RECEIVED AFTER ACCT EXPIRATION";}}
   if(reason!=0){rejected++;f.rejects.add(FixedWidth.text(d.format(),350)+FixedWidth.num(Integer.toString(reason),4)+FixedWidth.text(desc,76));continue;}
   TranCatBalRecord t=f.tcatbal.get(x.acctId+d.typeCd+d.catCd);if(t==null){t=new TranCatBalRecord();t.acctId=x.acctId;t.typeCd=d.typeCd;t.catCd=d.catCd;f.tcatbal.put(t.key(),t);}t.tranCatBal=t.tranCatBal.add(d.amt);
   a.currBal=a.currBal.add(d.amt);if(d.amt.signum()>=0)a.currCycCredit=a.currCycCredit.add(d.amt);else a.currCycDebit=a.currCycDebit.add(d.amt);f.accounts.put(a.acctId,a);
   TranRecord tr=DalyTranRecord.parse(d.format());tr.procTs=TS.format(Instant.now())+"0000";f.transact.put(tr.id,tr);
 }return new Result(processed,rejected,rejected>0?4:0);}
}
