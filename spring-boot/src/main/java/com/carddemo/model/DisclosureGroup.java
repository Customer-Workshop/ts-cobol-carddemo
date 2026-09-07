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
@Table(name = "disclosure_groups")
public class DisclosureGroup {
    @EmbeddedId private Id id;
    @Column(precision = 19, scale = 2) private BigDecimal interestRate;

    public Id getId() { return id; }
    public void setId(Id value) { id = value; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal value) { interestRate = value; }

    @Embeddable
    public static class Id implements Serializable {
        @jakarta.persistence.Column(length = 10, nullable = false) private String acctGroupId;
        @jakarta.persistence.Column(length = 2, nullable = false) private String tranTypeCode;
        @jakarta.persistence.Column(nullable = false) private Integer tranCategoryCode;
        public String getAcctGroupId() { return acctGroupId; }
        public void setAcctGroupId(String value) { acctGroupId = value; }
        public String getTranTypeCode() { return tranTypeCode; }
        public void setTranTypeCode(String value) { tranTypeCode = value; }
        public Integer getTranCategoryCode() { return tranCategoryCode; }
        public void setTranCategoryCode(Integer value) { tranCategoryCode = value; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Id that)) return false;
            return Objects.equals(acctGroupId, that.acctGroupId)
                    && Objects.equals(tranTypeCode, that.tranTypeCode)
                    && Objects.equals(tranCategoryCode, that.tranCategoryCode);
        }
        @Override public int hashCode() { return Objects.hash(acctGroupId, tranTypeCode, tranCategoryCode); }
    }
}
