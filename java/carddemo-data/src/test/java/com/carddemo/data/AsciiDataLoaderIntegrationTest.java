package com.carddemo.data;
import com.carddemo.data.entity.CardXref;
import com.carddemo.data.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"carddemo.loader.enabled=true","carddemo.loader.data-directory=../../app/data/ASCII","spring.datasource.url=jdbc:h2:mem:carddemo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class AsciiDataLoaderIntegrationTest {
 @Autowired AccountRepository accounts; @Autowired CustomerRepository customers; @Autowired CardRepository cards; @Autowired CardXrefRepository xrefs; @Autowired TransactionRepository transactions; @Autowired TransactionTypeRepository types; @Autowired TransactionCategoryRepository categories; @Autowired TransactionCategoryBalanceRepository balances;
 @Test void loadsAsciiSamplesAndJoinsCardAccountCustomer(){assertThat(accounts.count()).isEqualTo(50);assertThat(customers.count()).isEqualTo(50);assertThat(cards.count()).isEqualTo(50);assertThat(xrefs.count()).isEqualTo(50);assertThat(transactions.count()).isEqualTo(300);assertThat(types.count()).isEqualTo(7);assertThat(categories.count()).isEqualTo(18);assertThat(balances.count()).isEqualTo(50);CardXref x=xrefs.findByXrefCardNum("0500024453765740");assertThat(x.getAccount().getAcctId()).isEqualTo(50L);assertThat(x.getCustomer().getCustId()).isEqualTo(50L);}
}
