package com.carddemo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "transaction_types")
public class TransactionType {
    @Id @Column(length = 2, nullable = false) private String tranType;
    @Column(length = 50) private String description;
    public String getTranType() { return tranType; }
    public void setTranType(String value) { tranType = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
}
