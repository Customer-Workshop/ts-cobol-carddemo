package com.carddemo.cbact04c.service;
import java.io.*;import java.math.*;import java.nio.file.*;import java.time.*;import java.time.format.*;import java.util.*;import java.util.function.*;import org.slf4j.*;
import com.carddemo.cbact04c.domain.Records.*;import com.carddemo.cbact04c.io.*;
public class Cbact04cService {
 private static final Logger log=LoggerFactory.getLogger(Cbact04cService.class); private final Clock clock; private final boolean finalUpdate;
 public Cbact04cService(){this(Clock.systemDefaultZone(),false);} public Cbact04cService(Clock c,boolean f){clock=c;finalUpdate=f;}
 public BatchResult run(Path tcat,Path xref,Path disc,Path acct,Path transact,String parm){try{
  log.info("START OF EXECUTION OF PROGRAM CBACT04C"); List<TranCat> cats=FileGateways.lines(tcat).stream().map(RecordCodecs::tranCat).sorted(Comparator.comparing(TranCat::acctId).thenComparing(TranCat::typeCd).thenComparing(TranCat::catCd)).toList();
  Map<String,Xref> xs=FileGateways.xrefs(xref);Map<String,Account> as=FileGateways.accounts(acct);Map<DiscKey,DiscGroup> ds=FileGateways.discs(disc);List<String> out=new ArrayList<>();String last="           ";Account current=null;BigDecimal total=BigDecimal.ZERO;int suffix=0,count=0;
  DiscGroup holder=null;
  for(TranCat c:cats){count++;log.info(c.raw());if(!c.acctId().equals(last)){if(current!=null)flush(current,total,as);total=BigDecimal.ZERO;last=c.acctId();current=as.get(c.acctId());if(current==null){display("ACCOUNT NOT FOUND: "+c.acctId());abend("ERROR READING ACCOUNT FILE");}if(!xs.containsKey(c.acctId())){display("ACCOUNT NOT FOUND: "+c.acctId());abend("ERROR READING XREF FILE");}}
   DiscKey key=new DiscKey(current.groupId,c.typeCd(),c.catCd());DiscGroup found=ds.get(key);if(found==null){display("DISCLOSURE GROUP RECORD MISSING");display("TRY WITH DEFAULT GROUP CODE");found=ds.get(new DiscKey("DEFAULT   ",c.typeCd(),c.catCd()));if(found==null)abend("ERROR READING DEFAULT DISCLOSURE GROUP");else holder=found;}else holder=found;
   if(holder.rate().compareTo(BigDecimal.ZERO)!=0){BigDecimal monthly=c.balance().multiply(holder.rate()).divide(BigDecimal.valueOf(1200),20,RoundingMode.DOWN).setScale(2,RoundingMode.DOWN);total=total.add(monthly);Transaction t=new Transaction();t.id=parm+String.format("%06d",++suffix);t.typeCd="01";t.catCd="05";t.source="System";t.description="Int. for a/c "+current.id;t.amount=monthly;t.cardNum=xs.get(c.acctId()).cardNum();t.origTs=timestamp();t.procTs=t.origTs;out.add(RecordCodecs.transaction(t));}
  }
  if(finalUpdate&&current!=null)flush(current,total,as);FileGateways.writeAccounts(acct,as.values());Files.write(transact,out);log.info("END OF EXECUTION OF PROGRAM CBACT04C");return new BatchResult(count,out.size());
 }catch(AbendException e){throw e;}catch(Exception e){throw new AbendException("ABENDING PROGRAM",e);}}
 private void flush(Account a,BigDecimal total,Map<String,Account> as){a.balance=a.balance.add(total);a.currentCredit=BigDecimal.ZERO.setScale(2);a.currentDebit=BigDecimal.ZERO.setScale(2);as.put(a.id,a);}
 private String timestamp(){LocalDateTime d=LocalDateTime.now(clock);return d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS"))+"0000";}
 private void display(String s){log.error(s);} private void abend(String s){display(s);display("ABENDING PROGRAM");throw new AbendException(s);}
}
