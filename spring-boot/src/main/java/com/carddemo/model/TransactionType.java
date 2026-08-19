package com.carddemo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transaction_types")
public class TransactionType {
    @Id private String tranType;
    private String description;
    public String getTranType() { return tranType; }
    public void setTranType(String value) { tranType = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
}
