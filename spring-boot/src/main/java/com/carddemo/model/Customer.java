package com.carddemo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "customers")
public class Customer {
    @Id private Long custId;
    private String custFirstName;
    private String custMiddleName;
    private String custLastName;
    private String custAddrLine1;
    private String custAddrLine2;
    private String custAddrLine3;
    private String custAddrStateCode;
    private String custAddrCountryCode;
    private String custAddrZip;
    private String custPhoneNum1;
    private String custPhoneNum2;
    private Long custSsn;
    private String custGovernmentIssuedId;
    private LocalDate custDob;
    private String custEftAccountId;
    private String custPrimaryCardHolderIndicator;
    private Integer custFicoCreditScore;

    public Long getCustId() { return custId; }
    public void setCustId(Long value) { custId = value; }
    public String getCustFirstName() { return custFirstName; }
    public void setCustFirstName(String value) { custFirstName = value; }
    public String getCustMiddleName() { return custMiddleName; }
    public void setCustMiddleName(String value) { custMiddleName = value; }
    public String getCustLastName() { return custLastName; }
    public void setCustLastName(String value) { custLastName = value; }
    public String getCustAddrLine1() { return custAddrLine1; }
    public void setCustAddrLine1(String value) { custAddrLine1 = value; }
    public String getCustAddrLine2() { return custAddrLine2; }
    public void setCustAddrLine2(String value) { custAddrLine2 = value; }
    public String getCustAddrLine3() { return custAddrLine3; }
    public void setCustAddrLine3(String value) { custAddrLine3 = value; }
    public String getCustAddrStateCode() { return custAddrStateCode; }
    public void setCustAddrStateCode(String value) { custAddrStateCode = value; }
    public String getCustAddrCountryCode() { return custAddrCountryCode; }
    public void setCustAddrCountryCode(String value) { custAddrCountryCode = value; }
    public String getCustAddrZip() { return custAddrZip; }
    public void setCustAddrZip(String value) { custAddrZip = value; }
    public String getCustPhoneNum1() { return custPhoneNum1; }
    public void setCustPhoneNum1(String value) { custPhoneNum1 = value; }
    public String getCustPhoneNum2() { return custPhoneNum2; }
    public void setCustPhoneNum2(String value) { custPhoneNum2 = value; }
    public Long getCustSsn() { return custSsn; }
    public void setCustSsn(Long value) { custSsn = value; }
    public String getCustGovernmentIssuedId() { return custGovernmentIssuedId; }
    public void setCustGovernmentIssuedId(String value) { custGovernmentIssuedId = value; }
    public LocalDate getCustDob() { return custDob; }
    public void setCustDob(LocalDate value) { custDob = value; }
    public String getCustEftAccountId() { return custEftAccountId; }
    public void setCustEftAccountId(String value) { custEftAccountId = value; }
    public String getCustPrimaryCardHolderIndicator() { return custPrimaryCardHolderIndicator; }
    public void setCustPrimaryCardHolderIndicator(String value) { custPrimaryCardHolderIndicator = value; }
    public Integer getCustFicoCreditScore() { return custFicoCreditScore; }
    public void setCustFicoCreditScore(Integer value) { custFicoCreditScore = value; }
}
