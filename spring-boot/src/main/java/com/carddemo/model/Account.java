package com.carddemo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "accounts")
public class Account {
    @Id private Long acctId;
    private String acctActiveStatus;
    @Column(precision = 19, scale = 2) private BigDecimal acctCurrBal;
    @Column(precision = 19, scale = 2) private BigDecimal acctCreditLimit;
    @Column(precision = 19, scale = 2) private BigDecimal acctCashCreditLimit;
    private LocalDate acctOpenDate;
    private LocalDate acctExpirationDate;
    private LocalDate acctReissueDate;
    @Column(precision = 19, scale = 2) private BigDecimal acctCurrCycCredit;
    @Column(precision = 19, scale = 2) private BigDecimal acctCurrCycDebit;
    private String acctAddrZip;
    private String acctGroupId;

    public Long getAcctId() { return acctId; }
    public void setAcctId(Long value) { acctId = value; }
    public String getAcctActiveStatus() { return acctActiveStatus; }
    public void setAcctActiveStatus(String value) { acctActiveStatus = value; }
    public BigDecimal getAcctCurrBal() { return acctCurrBal; }
    public void setAcctCurrBal(BigDecimal value) { acctCurrBal = value; }
    public BigDecimal getAcctCreditLimit() { return acctCreditLimit; }
    public void setAcctCreditLimit(BigDecimal value) { acctCreditLimit = value; }
    public BigDecimal getAcctCashCreditLimit() { return acctCashCreditLimit; }
    public void setAcctCashCreditLimit(BigDecimal value) { acctCashCreditLimit = value; }
    public LocalDate getAcctOpenDate() { return acctOpenDate; }
    public void setAcctOpenDate(LocalDate value) { acctOpenDate = value; }
    public LocalDate getAcctExpirationDate() { return acctExpirationDate; }
    public void setAcctExpirationDate(LocalDate value) { acctExpirationDate = value; }
    public LocalDate getAcctReissueDate() { return acctReissueDate; }
    public void setAcctReissueDate(LocalDate value) { acctReissueDate = value; }
    public BigDecimal getAcctCurrCycCredit() { return acctCurrCycCredit; }
    public void setAcctCurrCycCredit(BigDecimal value) { acctCurrCycCredit = value; }
    public BigDecimal getAcctCurrCycDebit() { return acctCurrCycDebit; }
    public void setAcctCurrCycDebit(BigDecimal value) { acctCurrCycDebit = value; }
    public String getAcctAddrZip() { return acctAddrZip; }
    public void setAcctAddrZip(String value) { acctAddrZip = value; }
    public String getAcctGroupId() { return acctGroupId; }
    public void setAcctGroupId(String value) { acctGroupId = value; }
}
