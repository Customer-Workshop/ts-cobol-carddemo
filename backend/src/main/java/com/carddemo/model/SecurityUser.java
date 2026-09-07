package com.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * SEC-USER-DATA, app/cpy/CSUSR01Y.cpy:17-23 (VSAM USRSEC, B-0006).
 * SEC-USR-PWD is split into a BCrypt hash and the legacy plaintext kept only for upgrade-on-login (B-0026).
 */
@Entity
@Table(name = "users")
public class SecurityUser {

    @Id
    @Column(name = "sec_usr_id", length = 8, nullable = false)
    private String secUsrId;

    @Column(name = "sec_usr_fname", length = 20)
    private String secUsrFname;

    @Column(name = "sec_usr_lname", length = 20)
    private String secUsrLname;

    @Column(name = "sec_usr_pwd_hash", length = 72)
    private String secUsrPwdHash;

    @Column(name = "sec_usr_pwd_legacy", length = 8)
    private String secUsrPwdLegacy;

    @Column(name = "sec_usr_type", length = 1, nullable = false)
    private String secUsrType;

    public String getSecUsrId() {
        return secUsrId;
    }

    public void setSecUsrId(String secUsrId) {
        this.secUsrId = secUsrId;
    }

    public String getSecUsrFname() {
        return secUsrFname;
    }

    public void setSecUsrFname(String secUsrFname) {
        this.secUsrFname = secUsrFname;
    }

    public String getSecUsrLname() {
        return secUsrLname;
    }

    public void setSecUsrLname(String secUsrLname) {
        this.secUsrLname = secUsrLname;
    }

    public String getSecUsrPwdHash() {
        return secUsrPwdHash;
    }

    public void setSecUsrPwdHash(String secUsrPwdHash) {
        this.secUsrPwdHash = secUsrPwdHash;
    }

    public String getSecUsrPwdLegacy() {
        return secUsrPwdLegacy;
    }

    public void setSecUsrPwdLegacy(String secUsrPwdLegacy) {
        this.secUsrPwdLegacy = secUsrPwdLegacy;
    }

    public String getSecUsrType() {
        return secUsrType;
    }

    public void setSecUsrType(String secUsrType) {
        this.secUsrType = secUsrType;
    }
}
