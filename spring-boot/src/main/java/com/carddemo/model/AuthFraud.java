package com.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * CARDDEMO.AUTHFRDS journal row (AUTHFRDS.dcl): the DB2-side record of a
 * fraud report/remove action, keyed by card number plus the real
 * (uncomplemented) auth timestamp.
 */
@Entity
@Table(name = "auth_frauds")
public class AuthFraud {
    @EmbeddedId private Id id;
    @Column(length = 4) private String authType;
    @Column(length = 4) private String cardExpiryDate;
    @Column(length = 6) private String messageType;
    @Column(length = 6) private String messageSource;
    @Column(length = 6) private String authIdCode;
    @Column(length = 2) private String authRespCode;
    @Column(length = 4) private String authRespReason;
    @Column(length = 6) private String processingCode;
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
    private LocalDate fraudRptDate;
    private Long acctId;
    private Long custId;

    public Id getId() { return id; }
    public void setId(Id value) { id = value; }
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
    public String getProcessingCode() { return processingCode; }
    public void setProcessingCode(String value) { processingCode = value; }
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
    public LocalDate getFraudRptDate() { return fraudRptDate; }
    public void setFraudRptDate(LocalDate value) { fraudRptDate = value; }
    public Long getAcctId() { return acctId; }
    public void setAcctId(Long value) { acctId = value; }
    public Long getCustId() { return custId; }
    public void setCustId(Long value) { custId = value; }

    @Embeddable
    public static class Id implements Serializable {
        @Column(length = 16, nullable = false) private String cardNum;
        @Column(nullable = false) private LocalDateTime authTs;

        public Id() {
        }

        public Id(String cardNum, LocalDateTime authTs) {
            this.cardNum = cardNum;
            this.authTs = authTs;
        }

        public String getCardNum() { return cardNum; }
        public void setCardNum(String value) { cardNum = value; }
        public LocalDateTime getAuthTs() { return authTs; }
        public void setAuthTs(LocalDateTime value) { authTs = value; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Id that)) return false;
            return Objects.equals(cardNum, that.cardNum)
                    && Objects.equals(authTs, that.authTs);
        }

        @Override public int hashCode() {
            return Objects.hash(cardNum, authTs);
        }
    }
}
