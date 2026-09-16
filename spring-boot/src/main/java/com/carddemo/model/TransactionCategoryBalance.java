package com.carddemo.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "transaction_category_balances")
public class TransactionCategoryBalance {
    @EmbeddedId private Id id;
    @Column(precision = 19, scale = 2) private BigDecimal balance;
    public Id getId() { return id; }
    public void setId(Id value) { id = value; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal value) { balance = value; }

    @Embeddable
    public static class Id implements Serializable {
        @jakarta.persistence.Column(nullable = false) private Long acctId;
        @jakarta.persistence.Column(length = 2, nullable = false) private String typeCode;
        @jakarta.persistence.Column(nullable = false) private Integer categoryCode;
        public Long getAcctId() { return acctId; }
        public void setAcctId(Long value) { acctId = value; }
        public String getTypeCode() { return typeCode; }
        public void setTypeCode(String value) { typeCode = value; }
        public Integer getCategoryCode() { return categoryCode; }
        public void setCategoryCode(Integer value) { categoryCode = value; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Id that)) return false;
            return Objects.equals(acctId, that.acctId)
                    && Objects.equals(typeCode, that.typeCode)
                    && Objects.equals(categoryCode, that.categoryCode);
        }
        @Override public int hashCode() { return Objects.hash(acctId, typeCode, categoryCode); }
    }
}
