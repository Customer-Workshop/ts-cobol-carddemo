package com.carddemo.data.entity;
import jakarta.persistence.*;
@Entity @Table(name="TRAN_CATEGORY")
public class TransactionCategory { @EmbeddedId private TransactionCategoryId id; @Column(name="tran_cat_type_desc") private String description; public TransactionCategoryId getId(){return id;} public void setId(TransactionCategoryId v){id=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;} }
