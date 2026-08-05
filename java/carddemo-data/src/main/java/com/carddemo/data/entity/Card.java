package com.carddemo.data.entity;
import jakarta.persistence.*; import java.util.*;
@Entity @Table(name="CARD")
public class Card { @Id @Column(name="card_num") private String cardNum; @ManyToOne @JoinColumn(name="acct_id") private Account account; private Integer cvvCd; private String embossedName,expirationDate,activeStatus; @OneToMany(mappedBy="card") private List<Transaction> transactions=new ArrayList<>();
 public String getCardNum(){return cardNum;} public void setCardNum(String v){cardNum=v;} public Account getAccount(){return account;} public void setAccount(Account v){account=v;} public Integer getCvvCd(){return cvvCd;} public void setCvvCd(Integer v){cvvCd=v;} public String getEmbossedName(){return embossedName;} public void setEmbossedName(String v){embossedName=v;} public String getExpirationDate(){return expirationDate;} public void setExpirationDate(String v){expirationDate=v;} public String getActiveStatus(){return activeStatus;} public void setActiveStatus(String v){activeStatus=v;}
}
