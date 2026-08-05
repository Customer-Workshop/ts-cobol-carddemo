package com.carddemo.data.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Embeddable
public class TransactionCategoryId implements Serializable {
  private String tranTypeCd;
  private Integer tranCatCd;

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

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TransactionCategoryId x)) return false;
    return java.util.Objects.equals(tranTypeCd, x.tranTypeCd)
        && java.util.Objects.equals(tranCatCd, x.tranCatCd);
  }

  public int hashCode() {
    return java.util.Objects.hash(tranTypeCd, tranCatCd);
  }
}
