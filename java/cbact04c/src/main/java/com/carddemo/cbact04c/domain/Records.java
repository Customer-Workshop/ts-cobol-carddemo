package com.carddemo.cbact04c.domain;

import java.math.BigDecimal;

public final class Records {

    private Records() {
    }

    public record TranCat(
            String acctId,
            String typeCd,
            String catCd,
            BigDecimal balance,
            String raw) {
    }

    public record Xref(
            String cardNum,
            String custId,
            String acctId,
            String raw) {
    }

    public record DiscKey(
            String groupId,
            String typeCd,
            String catCd) {
    }

    public record DiscGroup(
            DiscKey key,
            BigDecimal rate,
            String raw) {
    }

    public static final class Account {

        public String id;
        public String status;
        public BigDecimal balance;
        public BigDecimal creditLimit;
        public BigDecimal cashCreditLimit;
        public String openDate;
        public String expirationDate;
        public String reissueDate;
        public BigDecimal currentCredit;
        public BigDecimal currentDebit;
        public String zip;
        public String groupId;
        public String raw;
    }

    public static final class Transaction {

        public String id;
        public String typeCd;
        public String catCd;
        public String source;
        public String description;
        public BigDecimal amount;
        public String merchantId;
        public String merchantName;
        public String merchantCity;
        public String merchantZip;
        public String cardNum;
        public String origTs;
        public String procTs;
    }
}
