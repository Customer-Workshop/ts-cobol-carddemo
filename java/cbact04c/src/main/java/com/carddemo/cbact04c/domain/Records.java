package com.carddemo.cbact04c.domain;
import java.math.BigDecimal;
public final class Records {
  private Records(){}
  public record TranCat(String acctId,String typeCd,String catCd,BigDecimal balance,String raw) {}
  public record Xref(String cardNum,String custId,String acctId,String raw) {}
  public record DiscKey(String groupId,String typeCd,String catCd) {}
  public record DiscGroup(DiscKey key,BigDecimal rate,String raw) {}
  public static final class Account {
    public String id,status,openDate,expirationDate,reissueDate,zip,groupId,raw;
    public BigDecimal balance,creditLimit,cashCreditLimit,currentCredit,currentDebit;
  }
  public static final class Transaction {
    public String id,typeCd,catCd,source,description,cardNum,origTs,procTs;
    public BigDecimal amount; public String raw;
  }
}
