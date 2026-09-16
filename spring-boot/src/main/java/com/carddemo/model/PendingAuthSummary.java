package com.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * IMS PAUTSUM0 root segment (CIPAUSMY.cpy): one pending-authorization
 * summary per account. PA-ACCOUNT-STATUS is X(02) OCCURS 5 — stored as the
 * raw 10-char concatenation the BMS ACCSTAT field displays.
 */
@Entity
@Table(name = "pending_auth_summary")
public class PendingAuthSummary {
    @Id @Column(nullable = false) private Long acctId;
    private Long custId;
    @Column(length = 1) private String authStatus;
    @Column(length = 10) private String accountStatus;
    @Column(precision = 11, scale = 2) private BigDecimal creditLimit;
    @Column(precision = 11, scale = 2) private BigDecimal cashLimit;
    @Column(precision = 11, scale = 2) private BigDecimal creditBalance;
    @Column(precision = 11, scale = 2) private BigDecimal cashBalance;
    private Integer approvedAuthCnt;
    private Integer declinedAuthCnt;
    @Column(precision = 11, scale = 2) private BigDecimal approvedAuthAmt;
    @Column(precision = 11, scale = 2) private BigDecimal declinedAuthAmt;

    public Long getAcctId() { return acctId; }
    public void setAcctId(Long value) { acctId = value; }
    public Long getCustId() { return custId; }
    public void setCustId(Long value) { custId = value; }
    public String getAuthStatus() { return authStatus; }
    public void setAuthStatus(String value) { authStatus = value; }
    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String value) { accountStatus = value; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal value) { creditLimit = value; }
    public BigDecimal getCashLimit() { return cashLimit; }
    public void setCashLimit(BigDecimal value) { cashLimit = value; }
    public BigDecimal getCreditBalance() { return creditBalance; }
    public void setCreditBalance(BigDecimal value) { creditBalance = value; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public void setCashBalance(BigDecimal value) { cashBalance = value; }
    public Integer getApprovedAuthCnt() { return approvedAuthCnt; }
    public void setApprovedAuthCnt(Integer value) { approvedAuthCnt = value; }
    public Integer getDeclinedAuthCnt() { return declinedAuthCnt; }
    public void setDeclinedAuthCnt(Integer value) { declinedAuthCnt = value; }
    public BigDecimal getApprovedAuthAmt() { return approvedAuthAmt; }
    public void setApprovedAuthAmt(BigDecimal value) { approvedAuthAmt = value; }
    public BigDecimal getDeclinedAuthAmt() { return declinedAuthAmt; }
    public void setDeclinedAuthAmt(BigDecimal value) { declinedAuthAmt = value; }
}
