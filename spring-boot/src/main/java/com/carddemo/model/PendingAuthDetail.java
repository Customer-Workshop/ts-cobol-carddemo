package com.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * IMS PAUTDTL1 child segment (CIPAUDTY.cpy): one pending authorization.
 * The segment key PAUT9CTS is the (acct_id, auth_date_9c, auth_time_9c)
 * composite of the two 9's-complement fields — the raw complement values
 * are stored so ascending order stays newest-first (S19-B3) and S-20 can
 * ISRT with the same bytes the producer computes.
 */
@Entity
@Table(name = "pending_auth_detail")
public class PendingAuthDetail {
    @EmbeddedId private Id id;
    @Column(length = 6) private String authOrigDate;
    @Column(length = 6) private String authOrigTime;
    @Column(length = 16) private String cardNum;
    @Column(length = 4) private String authType;
    @Column(length = 4) private String cardExpiryDate;
    @Column(length = 6) private String messageType;
    @Column(length = 6) private String messageSource;
    @Column(length = 6) private String authIdCode;
    @Column(length = 2) private String authRespCode;
    @Column(length = 4) private String authRespReason;
    private Integer processingCode;
    @Column(precision = 12, scale = 2) private BigDecimal transactionAmt;
    @Column(precision = 12, scale = 2) private BigDecimal approvedAmt;
    @Column(length = 4) private String merchantCategoryCode;
    @Column(length = 3) private String acqrCountryCode;
    private Integer posEntryMode;
    @Column(length = 15) private String merchantId;
    @Column(length = 22) private String merchantName;
    @Column(length = 13) private String merchantCity;
    @Column(length = 2) private String merchantState;
    @Column(length = 9) private String merchantZip;
    @Column(length = 15) private String transactionId;
    @Column(length = 1) private String matchStatus;
    @Column(length = 1) private String authFraud;
    @Column(length = 8) private String fraudRptDate;

    public Id getId() { return id; }
    public void setId(Id value) { id = value; }
    public String getAuthOrigDate() { return authOrigDate; }
    public void setAuthOrigDate(String value) { authOrigDate = value; }
    public String getAuthOrigTime() { return authOrigTime; }
    public void setAuthOrigTime(String value) { authOrigTime = value; }
    public String getCardNum() { return cardNum; }
    public void setCardNum(String value) { cardNum = value; }
    public String getAuthType() { return authType; }
    public void setAuthType(String value) { authType = value; }
    public String getCardExpiryDate() { return cardExpiryDate; }
    public void setCardExpiryDate(String value) { cardExpiryDate = value; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String value) { messageType = value; }
    public String getMessageSource() { return messageSource; }
    public void setMessageSource(String value) { messageSource = value; }
    public String getAuthIdCode() { return authIdCode; }
    public void setAuthIdCode(String value) { authIdCode = value; }
    public String getAuthRespCode() { return authRespCode; }
    public void setAuthRespCode(String value) { authRespCode = value; }
    public String getAuthRespReason() { return authRespReason; }
    public void setAuthRespReason(String value) { authRespReason = value; }
    public Integer getProcessingCode() { return processingCode; }
    public void setProcessingCode(Integer value) { processingCode = value; }
    public BigDecimal getTransactionAmt() { return transactionAmt; }
    public void setTransactionAmt(BigDecimal value) { transactionAmt = value; }
    public BigDecimal getApprovedAmt() { return approvedAmt; }
    public void setApprovedAmt(BigDecimal value) { approvedAmt = value; }
    public String getMerchantCategoryCode() { return merchantCategoryCode; }
    public void setMerchantCategoryCode(String value) { merchantCategoryCode = value; }
    public String getAcqrCountryCode() { return acqrCountryCode; }
    public void setAcqrCountryCode(String value) { acqrCountryCode = value; }
    public Integer getPosEntryMode() { return posEntryMode; }
    public void setPosEntryMode(Integer value) { posEntryMode = value; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String value) { merchantId = value; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String value) { merchantName = value; }
    public String getMerchantCity() { return merchantCity; }
    public void setMerchantCity(String value) { merchantCity = value; }
    public String getMerchantState() { return merchantState; }
    public void setMerchantState(String value) { merchantState = value; }
    public String getMerchantZip() { return merchantZip; }
    public void setMerchantZip(String value) { merchantZip = value; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String value) { transactionId = value; }
    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String value) { matchStatus = value; }
    public String getAuthFraud() { return authFraud; }
    public void setAuthFraud(String value) { authFraud = value; }
    public String getFraudRptDate() { return fraudRptDate; }
    public void setFraudRptDate(String value) { fraudRptDate = value; }

    @Embeddable
    public static class Id implements Serializable {
        @Column(nullable = false) private Long acctId;
        @Column(nullable = false) private Integer authDate9c;
        @Column(nullable = false) private Integer authTime9c;

        public Id() {
        }

        public Id(Long acctId, Integer authDate9c, Integer authTime9c) {
            this.acctId = acctId;
            this.authDate9c = authDate9c;
            this.authTime9c = authTime9c;
        }

        public Long getAcctId() { return acctId; }
        public void setAcctId(Long value) { acctId = value; }
        public Integer getAuthDate9c() { return authDate9c; }
        public void setAuthDate9c(Integer value) { authDate9c = value; }
        public Integer getAuthTime9c() { return authTime9c; }
        public void setAuthTime9c(Integer value) { authTime9c = value; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Id that)) return false;
            return Objects.equals(acctId, that.acctId)
                    && Objects.equals(authDate9c, that.authDate9c)
                    && Objects.equals(authTime9c, that.authTime9c);
        }

        @Override public int hashCode() {
            return Objects.hash(acctId, authDate9c, authTime9c);
        }
    }
}
