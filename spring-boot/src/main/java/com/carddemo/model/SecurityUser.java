package com.carddemo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "security_users")
public class SecurityUser {
    @Id private String userId;
    private String firstName;
    private String lastName;
    private String password;
    private String userType;

    public String getUserId() { return userId; }
    public void setUserId(String value) { userId = value; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String value) { firstName = value; }
    public String getLastName() { return lastName; }
    public void setLastName(String value) { lastName = value; }
    public String getPassword() { return password; }
    public void setPassword(String value) { password = value; }
    public String getUserType() { return userType; }
    public void setUserType(String value) { userType = value; }
}
