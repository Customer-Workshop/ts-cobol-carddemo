package com.carddemo.data.entity;
import jakarta.persistence.*;
@Entity @Table(name="TRAN_TYPE")
public class TransactionType { @Id @Column(name="tran_type") private String tranType; @Column(name="tran_type_desc") private String description; public String getTranType(){return tranType;} public void setTranType(String v){tranType=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;} }
