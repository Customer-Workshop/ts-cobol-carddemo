package com.carddemo.data.entity;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="TRAN_CAT_BAL")
public class TransactionCategoryBalance { @EmbeddedId private TransactionCategoryBalanceId id; @ManyToOne @MapsId("acctId") @JoinColumn(name="acct_id") private Account account; @Column(precision=11,scale=2) private BigDecimal balance; public TransactionCategoryBalanceId getId(){return id;} public void setId(TransactionCategoryBalanceId v){id=v;} public Account getAccount(){return account;} public void setAccount(Account v){account=v;} public BigDecimal getBalance(){return balance;} public void setBalance(BigDecimal v){balance=v;} }
