package com.carddemo.data.entity;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "CUSTOMER")
public class Customer {
  @Id
  @Column(name = "cust_id")
  private Long custId;

  private String firstName;
  private String middleName;
  private String lastName;

  @Column(name = "addr_line_1")
  private String addrLine1;

  @Column(name = "addr_line_2")
  private String addrLine2;

  @Column(name = "addr_line_3")
  private String addrLine3;

  private String addrStateCd;
  private String addrCountryCd;
  private String addrZip;

  @Column(name = "phone_num_1")
  private String phoneNum1;

  @Column(name = "phone_num_2")
  private String phoneNum2;

  private String ssn;
  private String govtIssuedId;
  private String dob;
  private String eftAccountId;
  private String primaryCardHolderInd;
  private Integer ficoCreditScore;

  @OneToMany(mappedBy = "customer")
  private List<CardXref> cardXrefs = new ArrayList<>();

  public Long getCustId() {
    return custId;
  }

  public void setCustId(Long v) {
    custId = v;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String v) {
    firstName = v;
  }

  public String getMiddleName() {
    return middleName;
  }

  public void setMiddleName(String v) {
    middleName = v;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String v) {
    lastName = v;
  }

  public String getAddrLine1() {
    return addrLine1;
  }

  public void setAddrLine1(String v) {
    addrLine1 = v;
  }

  public String getAddrLine2() {
    return addrLine2;
  }

  public void setAddrLine2(String v) {
    addrLine2 = v;
  }

  public String getAddrLine3() {
    return addrLine3;
  }

  public void setAddrLine3(String v) {
    addrLine3 = v;
  }

  public String getAddrStateCd() {
    return addrStateCd;
  }

  public void setAddrStateCd(String v) {
    addrStateCd = v;
  }

  public String getAddrCountryCd() {
    return addrCountryCd;
  }

  public void setAddrCountryCd(String v) {
    addrCountryCd = v;
  }

  public String getAddrZip() {
    return addrZip;
  }

  public void setAddrZip(String v) {
    addrZip = v;
  }

  public String getPhoneNum1() {
    return phoneNum1;
  }

  public void setPhoneNum1(String v) {
    phoneNum1 = v;
  }

  public String getPhoneNum2() {
    return phoneNum2;
  }

  public void setPhoneNum2(String v) {
    phoneNum2 = v;
  }

  public String getSsn() {
    return ssn;
  }

  public void setSsn(String v) {
    ssn = v;
  }

  public String getGovtIssuedId() {
    return govtIssuedId;
  }

  public void setGovtIssuedId(String v) {
    govtIssuedId = v;
  }

  public String getDob() {
    return dob;
  }

  public void setDob(String v) {
    dob = v;
  }

  public String getEftAccountId() {
    return eftAccountId;
  }

  public void setEftAccountId(String v) {
    eftAccountId = v;
  }

  public String getPrimaryCardHolderInd() {
    return primaryCardHolderInd;
  }

  public void setPrimaryCardHolderInd(String v) {
    primaryCardHolderInd = v;
  }

  public Integer getFicoCreditScore() {
    return ficoCreditScore;
  }

  public void setFicoCreditScore(Integer v) {
    ficoCreditScore = v;
  }
}
