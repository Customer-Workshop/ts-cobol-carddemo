package com.carddemo.cbact04c.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cbact04c")
public class Cbact04cProperties {

    private String tcatbal;
    private String xref;
    private String discgrp;
    private String account;
    private String transact;
    private String parmDate;
    private boolean finalUpdateAtEof;

    public String getTcatbal() {
        return tcatbal;
    }

    public void setTcatbal(String tcatbal) {
        this.tcatbal = tcatbal;
    }

    public String getXref() {
        return xref;
    }

    public void setXref(String xref) {
        this.xref = xref;
    }

    public String getDiscgrp() {
        return discgrp;
    }

    public void setDiscgrp(String discgrp) {
        this.discgrp = discgrp;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getTransact() {
        return transact;
    }

    public void setTransact(String transact) {
        this.transact = transact;
    }

    public String getParmDate() {
        return parmDate;
    }

    public void setParmDate(String parmDate) {
        this.parmDate = parmDate;
    }

    public boolean isFinalUpdateAtEof() {
        return finalUpdateAtEof;
    }

    public void setFinalUpdateAtEof(boolean finalUpdateAtEof) {
        this.finalUpdateAtEof = finalUpdateAtEof;
    }
}
