package com.carddemo.data.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "\"transaction\"")
public class Transaction {
  @Id
  @Column(name = "tran_id")
  private String tranId;

  private String tranTypeCd;
  private Integer tranCatCd;
  private String tranSource;
  private String tranDesc;

  @Column(precision = 11, scale = 2)
  private BigDecimal tranAmt;

  private Long merchantId;
  private String merchantName;
  private String merchantCity;
  private String merchantZip;

  @ManyToOne
  @JoinColumn(name = "card_num")
  private Card card;

  @ManyToOne
  @JoinColumn(name = "acct_id")
  private Account account;

  private String origTs;
  private String procTs;

  public String getTranId() {
    return tranId;
  }

  public void setTranId(String v) {
    tranId = v;
  }

  public String getTranTypeCd() {
    return tranTypeCd;
  }

  public void setTranTypeCd(String v) {
    tranTypeCd = v;
  }

  public Integer getTranCatCd() {
    return tranCatCd;
  }

  public void setTranCatCd(Integer v) {
    tranCatCd = v;
  }

  public String getTranSource() {
    return tranSource;
  }

  public void setTranSource(String v) {
    tranSource = v;
  }

  public String getTranDesc() {
    return tranDesc;
  }

  public void setTranDesc(String v) {
    tranDesc = v;
  }

  public BigDecimal getTranAmt() {
    return tranAmt;
  }

  public void setTranAmt(BigDecimal v) {
    tranAmt = v;
  }

  public Long getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(Long v) {
    merchantId = v;
  }

  public String getMerchantName() {
    return merchantName;
  }

  public void setMerchantName(String v) {
    merchantName = v;
  }

  public String getMerchantCity() {
    return merchantCity;
  }

  public void setMerchantCity(String v) {
    merchantCity = v;
  }

  public String getMerchantZip() {
    return merchantZip;
  }

  public void setMerchantZip(String v) {
    merchantZip = v;
  }

  public Card getCard() {
    return card;
  }

  public void setCard(Card v) {
    card = v;
  }

  public Account getAccount() {
    return account;
  }

  public void setAccount(Account v) {
    account = v;
  }

  public String getOrigTs() {
    return origTs;
  }

  public void setOrigTs(String v) {
    origTs = v;
  }

  public String getProcTs() {
    return procTs;
  }

  public void setProcTs(String v) {
    procTs = v;
  }
}
