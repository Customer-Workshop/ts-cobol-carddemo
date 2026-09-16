package com.carddemo.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "transaction_categories")
public class TransactionCategory {
    @EmbeddedId private Id id;
    private String description;
    public Id getId() { return id; }
    public void setId(Id value) { id = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }

    @Embeddable
    public static class Id implements Serializable {
        @jakarta.persistence.Column(length = 2, nullable = false) private String tranTypeCode;
        @jakarta.persistence.Column(nullable = false) private Integer tranCategoryCode;
        public String getTranTypeCode() { return tranTypeCode; }
        public void setTranTypeCode(String value) { tranTypeCode = value; }
        public Integer getTranCategoryCode() { return tranCategoryCode; }
        public void setTranCategoryCode(Integer value) { tranCategoryCode = value; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Id that)) return false;
            return Objects.equals(tranTypeCode, that.tranTypeCode)
                    && Objects.equals(tranCategoryCode, that.tranCategoryCode);
        }
        @Override public int hashCode() { return Objects.hash(tranTypeCode, tranCategoryCode); }
    }
}
