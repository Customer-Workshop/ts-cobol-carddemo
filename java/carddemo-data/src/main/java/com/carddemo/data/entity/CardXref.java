package com.carddemo.data.entity;
import jakarta.persistence.*;
@Entity @Table(name="CARD_XREF")
public class CardXref { @Id @Column(name="card_num") private String xrefCardNum; @ManyToOne @JoinColumn(name="cust_id") private Customer customer; @ManyToOne @JoinColumn(name="acct_id") private Account account;
 public String getXrefCardNum(){return xrefCardNum;} public void setXrefCardNum(String v){xrefCardNum=v;} public Customer getCustomer(){return customer;} public void setCustomer(Customer v){customer=v;} public Account getAccount(){return account;} public void setAccount(Account v){account=v;}
}
