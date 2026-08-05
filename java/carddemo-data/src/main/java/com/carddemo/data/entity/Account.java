package com.carddemo.data.entity;
import jakarta.persistence.*; import java.math.BigDecimal; import java.util.*;
@Entity @Table(name="ACCOUNT")
public class Account {
 @Id @Column(name="acct_id") private Long acctId; @Column(name="active_status") private String activeStatus;
 @Column(precision=12,scale=2) private BigDecimal currBal, creditLimit, cashCreditLimit, currCycCredit, currCycDebit;
 private String openDate, expirationDate, reissueDate, addrZip, groupId;
 @OneToMany(mappedBy="account") private List<Card> cards=new ArrayList<>();
 @OneToMany(mappedBy="account") private List<CardXref> cardXrefs=new ArrayList<>();
 @OneToMany(mappedBy="account") private List<Transaction> transactions=new ArrayList<>();
 @OneToMany(mappedBy="account") private List<TransactionCategoryBalance> categoryBalances=new ArrayList<>();
 public Long getAcctId(){return acctId;} public void setAcctId(Long v){acctId=v;} public String getActiveStatus(){return activeStatus;} public void setActiveStatus(String v){activeStatus=v;}
 public BigDecimal getCurrBal(){return currBal;} public void setCurrBal(BigDecimal v){currBal=v;} public BigDecimal getCreditLimit(){return creditLimit;} public void setCreditLimit(BigDecimal v){creditLimit=v;} public BigDecimal getCashCreditLimit(){return cashCreditLimit;} public void setCashCreditLimit(BigDecimal v){cashCreditLimit=v;} public BigDecimal getCurrCycCredit(){return currCycCredit;} public void setCurrCycCredit(BigDecimal v){currCycCredit=v;} public BigDecimal getCurrCycDebit(){return currCycDebit;} public void setCurrCycDebit(BigDecimal v){currCycDebit=v;}
 public String getOpenDate(){return openDate;} public void setOpenDate(String v){openDate=v;} public String getExpirationDate(){return expirationDate;} public void setExpirationDate(String v){expirationDate=v;} public String getReissueDate(){return reissueDate;} public void setReissueDate(String v){reissueDate=v;} public String getAddrZip(){return addrZip;} public void setAddrZip(String v){addrZip=v;} public String getGroupId(){return groupId;} public void setGroupId(String v){groupId=v;}
}
