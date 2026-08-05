package com.carddemo.data.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "USER_SECURITY")
public class UserSecurity {
  @Id
  @Column(name = "sec_usr_id")
  private String secUsrId;

  private String fname;
  private String lname;

  // TODO: Legacy passwords are plaintext; hash during a future security migration.
  private String pwd;
  private String type;

  public String getSecUsrId() {
    return secUsrId;
  }

  public void setSecUsrId(String v) {
    secUsrId = v;
  }

  public String getFname() {
    return fname;
  }

  public void setFname(String v) {
    fname = v;
  }

  public String getLname() {
    return lname;
  }

  public void setLname(String v) {
    lname = v;
  }

  public String getPwd() {
    return pwd;
  }

  public void setPwd(String v) {
    pwd = v;
  }

  public String getType() {
    return type;
  }

  public void setType(String v) {
    type = v;
  }
}
