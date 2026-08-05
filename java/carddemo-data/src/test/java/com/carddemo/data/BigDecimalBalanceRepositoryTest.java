package com.carddemo.data;

import com.carddemo.data.entity.Account;
import com.carddemo.data.repository.AccountRepository;
import java.math.BigDecimal;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "carddemo.loader.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:balance-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
    })
class BigDecimalBalanceRepositoryTest {
  @Autowired private AccountRepository accounts;

  @Test
  void persistsAndReadsBalanceWithExactValueAndScale() {
    BigDecimal expected = new BigDecimal("123456789.01");
    Account account = new Account();
    account.setAcctId(999L);
    account.setCurrBal(expected);
    accounts.saveAndFlush(account);

    BigDecimal actual = accounts.findById(999L).orElseThrow().getCurrBal();
    Assertions.assertThat(actual).isEqualByComparingTo(expected);
    Assertions.assertThat(actual.scale()).isEqualTo(2);
  }
}
