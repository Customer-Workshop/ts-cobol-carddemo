package com.carddemo.data;

import com.carddemo.data.entity.*;
import com.carddemo.data.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

@Component
public class AsciiDataLoader implements CommandLineRunner {
 private final CustomerRepository customers; private final AccountRepository accounts; private final TransactionTypeRepository types;
 private final TransactionCategoryRepository categories; private final CardRepository cards; private final CardXrefRepository xrefs;
 private final TransactionRepository transactions; private final TransactionCategoryBalanceRepository balances;
 private final Map<String,Long> cardAccounts=new HashMap<>();
 @Value("${carddemo.loader.enabled:false}") private boolean enabled; @Value("${carddemo.loader.data-directory:../../../app/data/ASCII}") private String directory;
 public AsciiDataLoader(CustomerRepository c,AccountRepository a,TransactionTypeRepository t,TransactionCategoryRepository tc,CardRepository ca,CardXrefRepository x,TransactionRepository tr,TransactionCategoryBalanceRepository b){customers=c;accounts=a;types=t;categories=tc;cards=ca;xrefs=x;transactions=tr;balances=b;}
 public void run(String... args) throws Exception { if(!enabled)return; Path d=Paths.get(directory); loadCustomers(d.resolve("custdata.txt")); loadAccounts(d.resolve("acctdata.txt")); loadTypes(d.resolve("trantype.txt")); loadCategories(d.resolve("trancatg.txt")); loadCards(d.resolve("carddata.txt")); loadXrefs(d.resolve("cardxref.txt")); loadTransactions(d.resolve("dailytran.txt")); loadBalances(d.resolve("tcatbal.txt")); }
 private Stream<String> lines(Path p) throws Exception { return Files.lines(p); }
 private String s(String r,int a,int n){return r.substring(a,Math.min(r.length(),a+n)).trim();}
 private Long n(String r,int a,int z){return Long.valueOf(s(r,a,z));} private Integer i(String r,int a,int z){return Integer.valueOf(s(r,a,z));}
 private BigDecimal money(String r,int a,int z){String v=r.substring(a,a+z); char c=v.charAt(z-1); int sign=1; if(c=='{'||c=='}'||(c>='A'&&c<='R')) { sign=(c=='}'||(c>='J'&&c<='R'))?-1:1; if(c=='{'||c=='}') c='0'; else c=(char)('0'+((c>='A'&&c<='I')?c-'A'+1:c-'J'+1)); v=v.substring(0,z-1)+c; } return new BigDecimal(v).movePointLeft(2).multiply(BigDecimal.valueOf(sign)).setScale(2); }
 private void loadCustomers(Path p)throws Exception{try(Stream<String> ls=lines(p)){ls.forEach(r->{Customer x=new Customer();x.setCustId(n(r,0,9));x.setFirstName(s(r,9,25));x.setMiddleName(s(r,34,25));x.setLastName(s(r,59,25));x.setAddrLine1(s(r,84,50));x.setAddrLine2(s(r,134,50));x.setAddrLine3(s(r,184,50));x.setAddrStateCd(s(r,234,2));x.setAddrCountryCd(s(r,236,3));x.setAddrZip(s(r,239,10));x.setPhoneNum1(s(r,249,15));x.setPhoneNum2(s(r,264,15));x.setSsn(s(r,279,9));x.setGovtIssuedId(s(r,288,20));x.setDob(s(r,308,10));x.setEftAccountId(s(r,318,10));x.setPrimaryCardHolderInd(s(r,328,1));x.setFicoCreditScore(i(r,329,3));customers.save(x);});}}
 private void loadAccounts(Path p)throws Exception{try(Stream<String> ls=lines(p)){ls.forEach(r->{Account x=new Account();x.setAcctId(n(r,0,11));x.setActiveStatus(s(r,11,1));x.setCurrBal(money(r,12,12));x.setCreditLimit(money(r,24,12));x.setCashCreditLimit(money(r,36,12));x.setOpenDate(s(r,48,10));x.setExpirationDate(s(r,58,10));x.setReissueDate(s(r,68,10));x.setCurrCycCredit(money(r,78,12));x.setCurrCycDebit(money(r,90,12));x.setAddrZip(s(r,102,10));x.setGroupId(s(r,112,10));accounts.save(x);});}}
 private void loadTypes(Path p)throws Exception{try(Stream<String> ls=lines(p)){ls.forEach(r->{TransactionType x=new TransactionType();x.setTranType(s(r,0,2));x.setDescription(s(r,2,50));types.save(x);});}}
 private void loadCategories(Path p)throws Exception{try(Stream<String> ls=lines(p)){ls.forEach(r->{TransactionCategory x=new TransactionCategory();TransactionCategoryId id=new TransactionCategoryId();id.setTranTypeCd(s(r,0,2));id.setTranCatCd(i(r,2,4));x.setId(id);x.setDescription(s(r,6,50));categories.save(x);});}}
 private void loadCards(Path p)throws Exception{try(Stream<String> ls=lines(p)){ls.forEach(r->{Card x=new Card();x.setCardNum(s(r,0,16));Long acct=n(r,16,11);cardAccounts.put(x.getCardNum(),acct);x.setAccount(accounts.getReferenceById(acct));x.setCvvCd(i(r,27,3));x.setEmbossedName(s(r,30,50));x.setExpirationDate(s(r,80,10));x.setActiveStatus(s(r,90,1));cards.save(x);});}}
 private void loadXrefs(Path p)throws Exception{try(Stream<String> ls=lines(p)){ls.forEach(r->{CardXref x=new CardXref();x.setXrefCardNum(s(r,0,16));x.setCustomer(customers.getReferenceById(n(r,16,9)));x.setAccount(accounts.getReferenceById(n(r,25,11)));xrefs.save(x);});}}
 private void loadTransactions(Path p)throws Exception{try(Stream<String> ls=lines(p)){ls.forEach(r->{Transaction x=new Transaction();x.setTranId(s(r,0,16));x.setTranTypeCd(s(r,16,2));x.setTranCatCd(i(r,18,4));x.setTranSource(s(r,22,10));x.setTranDesc(s(r,32,100));x.setTranAmt(money(r,132,11));x.setMerchantId(n(r,143,9));x.setMerchantName(s(r,152,50));x.setMerchantCity(s(r,202,50));x.setMerchantZip(s(r,252,10));String cardNum=s(r,262,16);x.setCard(cards.getReferenceById(cardNum));x.setAccount(accounts.getReferenceById(cardAccounts.get(cardNum)));x.setOrigTs(s(r,278,26));x.setProcTs(s(r,304,26));transactions.save(x);});}}
 private void loadBalances(Path p)throws Exception{try(Stream<String> ls=lines(p)){ls.forEach(r->{TransactionCategoryBalance x=new TransactionCategoryBalance();TransactionCategoryBalanceId id=new TransactionCategoryBalanceId();id.setAcctId(n(r,0,11));id.setTypeCd(s(r,11,2));id.setCatCd(i(r,13,4));x.setId(id);x.setAccount(accounts.getReferenceById(id.getAcctId()));x.setBalance(money(r,17,11));balances.save(x);});}}
}
