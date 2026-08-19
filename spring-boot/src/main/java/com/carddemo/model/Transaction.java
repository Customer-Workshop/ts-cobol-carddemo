package com.carddemo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id private String tranId;
    private String tranTypeCode;
    private Integer tranCategoryCode;
    private String tranSource;
    private String tranDescription;
    private BigDecimal tranAmount;
    private Long tranMerchantId;
    private String tranMerchantName;
    private String tranMerchantCity;
    private String tranMerchantZip;
    private String tranCardNumber;
    private LocalDateTime tranOriginTimestamp;
    private LocalDateTime tranProcessTimestamp;

    public String getTranId() { return tranId; }
    public void setTranId(String value) { tranId = value; }
    public String getTranTypeCode() { return tranTypeCode; }
    public void setTranTypeCode(String value) { tranTypeCode = value; }
    public Integer getTranCategoryCode() { return tranCategoryCode; }
    public void setTranCategoryCode(Integer value) { tranCategoryCode = value; }
    public String getTranSource() { return tranSource; }
    public void setTranSource(String value) { tranSource = value; }
    public String getTranDescription() { return tranDescription; }
    public void setTranDescription(String value) { tranDescription = value; }
    public BigDecimal getTranAmount() { return tranAmount; }
    public void setTranAmount(BigDecimal value) { tranAmount = value; }
    public Long getTranMerchantId() { return tranMerchantId; }
    public void setTranMerchantId(Long value) { tranMerchantId = value; }
    public String getTranMerchantName() { return tranMerchantName; }
    public void setTranMerchantName(String value) { tranMerchantName = value; }
    public String getTranMerchantCity() { return tranMerchantCity; }
    public void setTranMerchantCity(String value) { tranMerchantCity = value; }
    public String getTranMerchantZip() { return tranMerchantZip; }
    public void setTranMerchantZip(String value) { tranMerchantZip = value; }
    public String getTranCardNumber() { return tranCardNumber; }
    public void setTranCardNumber(String value) { tranCardNumber = value; }
    public LocalDateTime getTranOriginTimestamp() { return tranOriginTimestamp; }
    public void setTranOriginTimestamp(LocalDateTime value) { tranOriginTimestamp = value; }
    public LocalDateTime getTranProcessTimestamp() { return tranProcessTimestamp; }
    public void setTranProcessTimestamp(LocalDateTime value) { tranProcessTimestamp = value; }
}
