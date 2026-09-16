package com.carddemo;

import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.service.CobolFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COACTUPC REST seam: POST /api/accounts/lookup (search+read), POST
 * /api/accounts/validate (the edit ladder), PUT /api/accounts/{id} (the
 * READ UPDATE/COMPARE/REWRITE save). FR-S03 rows in the test names.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:accountupdateit;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountUpdateIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CardXrefRepository xrefRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSource dataSource;

    @BeforeEach
    void addRegularUser() {
        SecurityUser user = new SecurityUser();
        user.setUserId("USER0001");
        user.setFirstName("REGULAR");
        user.setLastName("USER");
        user.setPassword("PASSWORD");
        user.setUserType("U");
        userRepository.save(user);
    }

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "USER0001").param("passwd", "PASSWORD"))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private JsonNode lookup(MockHttpSession session, String accountId)
            throws Exception {
        return objectMapper.readTree(mockMvc.perform(
                        post("/api/accounts/lookup").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"accountId\":\"" + accountId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** The request the confirm screen would send back: echoed form+snapshot. */
    private ObjectNode request(JsonNode details) {
        ObjectNode request = objectMapper.createObjectNode();
        request.set("updated", details.get("fields").deepCopy());
        request.set("original", details.get("original").deepCopy());
        return request;
    }

    /** `fields` repaired to ladder-passing values (seed zip/eft/phone fail). */
    private ObjectNode validRequest(JsonNode details) {
        ObjectNode request = request(details);
        ObjectNode updated = (ObjectNode) request.get("updated");
        updated.put("zip", "10100");
        updated.put("eftAccountId", "1234567890");
        updated.put("phone1a", "201");
        updated.put("phone1b", "555");
        updated.put("phone1c", "1212");
        // A stored "()-" renders as ")- " which fails the numeric edit —
        // an earlier save can leave that behind, so clear the parts.
        updated.put("phone2a", "");
        updated.put("phone2b", "");
        updated.put("phone2c", "");
        updated.put("dobYear", "1950");
        updated.put("dobMon", "12");
        updated.put("dobDay", "10");
        updated.put("city", "Boston");
        // Tests share the H2 database, so always differ from the fetched
        // snapshot to clear the 1205 no-change gate.
        int fico = request.get("original").get("ficoScore").asInt();
        updated.put("ficoScore", fico == 801 ? "802" : "801");
        return request;
    }

    private static Account account(long id) {
        Account a = new Account();
        a.setAcctId(id);
        a.setAcctActiveStatus("Y");
        a.setAcctCurrBal(new BigDecimal("194.00"));
        a.setAcctCreditLimit(new BigDecimal("2020.00"));
        a.setAcctCashCreditLimit(new BigDecimal("1020.00"));
        a.setAcctOpenDate(java.time.LocalDate.of(2020, 1, 1));
        a.setAcctExpirationDate(java.time.LocalDate.of(2025, 1, 1));
        a.setAcctReissueDate(java.time.LocalDate.of(2025, 1, 1));
        a.setAcctCurrCycCredit(BigDecimal.ZERO);
        a.setAcctCurrCycDebit(BigDecimal.ZERO);
        a.setAcctGroupId("02108");
        return a;
    }

    private static CardXref xref(long acctId, long custId) {
        CardXref x = new CardXref();
        x.setXrefCardNumber("9999888877776666");
        x.setXrefCustId(custId);
        x.setXrefAcctId(acctId);
        return x;
    }

    @Test
    void searchEditRejectsBlankAndBadIds_frS0302_frS0303() throws Exception {
        MockHttpSession session = signon();
        for (String id : new String[] {"", "   ", "*"}) {
            mockMvc.perform(post("/api/accounts/lookup").session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accountId\":\"" + id + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("SEARCH"))
                    .andExpect(jsonPath("$.errorMessage")
                            .value("No input received"));
        }
        for (String id : new String[] {"1", "00000000000"}) {
            mockMvc.perform(post("/api/accounts/lookup").session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accountId\":\"" + id + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errorMessage").value(
                            "Account Number if supplied must be a 11 digit"
                                    + " Non-Zero Number"));
        }
    }

    @Test
    void lookupFailsInXrefAccountCustomerOrder_frS0304_frS0305_frS0306()
            throws Exception {
        MockHttpSession session = signon();
        // No xref row for 2.
        mockMvc.perform(post("/api/accounts/lookup").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"00000000002\"}"))
                .andExpect(jsonPath("$.errorMessage").value(
                        "Account:00000000002 not found in Cross ref file.  "
                                + "Resp:000000013  Reas:0000"));

        // Xref present, account master absent (D2 — stops with the message).
        xrefRepository.save(xref(5L, 1L));
        mockMvc.perform(post("/api/accounts/lookup").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"00000000005\"}"))
                .andExpect(jsonPath("$.errorMessage").value(
                        "Account:00000000005 not found in Acct Master file."
                                + "Resp:000000013  Reas:0000"));

        // Account present, customer master absent.
        accountRepository.save(account(6L));
        xrefRepository.save(xref(6L, 999L));
        mockMvc.perform(post("/api/accounts/lookup").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"00000000006\"}"))
                .andExpect(jsonPath("$.errorMessage").value(
                        "CustId:000000999 not found in customer master."
                                + "Resp: 000000013  REAS:0000000"));
    }

    @Test
    void lookupShowsDerivedOriginals_frS0307() throws Exception {
        MockHttpSession session = signon();
        JsonNode details = lookup(session, "00000000001");
        assertThat(details.get("state").asText()).isEqualTo("DETAILS");
        assertThat(details.get("infoMessage").asText())
                .isEqualTo("Update account details presented above.");
        JsonNode f = details.get("fields");
        assertThat(f.get("activeStatus").asText()).isEqualTo("Y");
        assertThat(f.get("creditLimit").asText())
                .isEqualTo(CobolFormat.editSignedAmount(new BigDecimal("2020.00")));
        assertThat(f.get("custId").asText()).isEqualTo("000000001");
        assertThat(f.get("ssn1").asText()).isEqualTo("123");
        assertThat(f.get("ssn3").asText()).isEqualTo("6789");
        assertThat(f.get("zip").asText()).isEqualTo(
                customerRepository.findById(1L).orElseThrow()
                        .getCustAddrZip().substring(0, 5));
        assertThat(f.get("country").asText()).isEqualTo("USA");
        assertThat(details.get("original").get("accountId").asLong()).isEqualTo(1L);
    }

    @Test
    void validateUnchangedAndBadEdits_frS0308_frS0324() throws Exception {
        MockHttpSession session = signon();
        JsonNode details = lookup(session, "00000000001");
        mockMvc.perform(post("/api/accounts/validate").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request(details))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DETAILS"))
                .andExpect(jsonPath("$.errorMessage").value(
                        "No change detected with respect to values fetched."));

        ObjectNode bad = validRequest(details);
        ((ObjectNode) bad.get("updated")).put("activeStatus", "X");
        mockMvc.perform(post("/api/accounts/validate").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(bad)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EDIT_ERROR"))
                .andExpect(jsonPath("$.errorMessage")
                        .value("Account Status must be Y or N."))
                .andExpect(jsonPath("$.flags.acsttus").value("NOT_OK"));
    }

    @Test
    void validateValidChangesReachConfirm_frS0323() throws Exception {
        MockHttpSession session = signon();
        JsonNode details = lookup(session, "00000000001");
        mockMvc.perform(post("/api/accounts/validate").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                validRequest(details))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CONFIRM"))
                .andExpect(jsonPath("$.infoMessage")
                        .value("Changes validated.Press F5 to save"));
    }

    @Test
    void saveWritesBothRowsAndReportsDone_frS0325_frS0334() throws Exception {
        MockHttpSession session = signon();
        JsonNode details = lookup(session, "00000000001");
        ObjectNode body = validRequest(details);
        mockMvc.perform(put("/api/accounts/1").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DONE"))
                .andExpect(jsonPath("$.infoMessage")
                        .value("Changes committed to database"));

        var customer = customerRepository.findById(1L).orElseThrow();
        int expectedFico = details.get("original").get("ficoScore").asInt() == 801
                ? 802 : 801;
        assertThat(customer.getCustFicoCreditScore()).isEqualTo(expectedFico);
        assertThat(customer.getCustPhoneNum1()).isEqualTo("(201)555-1212");
        assertThat(customer.getCustPhoneNum2()).isEqualTo("()-");
        assertThat(customer.getCustAddrZip()).isEqualTo("10100");
        assertThat(customer.getCustSsn()).isEqualTo(123456789L);
        var account = accountRepository.findById(1L).orElseThrow();
        assertThat(account.getAcctCurrBal()).isEqualByComparingTo(
                new BigDecimal(body.get("updated").get("currentBalance")
                        .asText().replaceAll("[+,\\s]", "")));
    }

    @Test
    void accountLockContentionReportsFailure_frS0326() throws Exception {
        MockHttpSession session = signon();
        JsonNode details = lookup(session, "00000000001");
        ObjectNode body = validRequest(details);
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT acct_id FROM accounts WHERE acct_id = ? FOR UPDATE")) {
                ps.setLong(1, 1L);
                ps.executeQuery();
            }
            mockMvc.perform(put("/api/accounts/1").session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("FAILED"))
                    .andExpect(jsonPath("$.errorMessage")
                            .value("Could not lock account record for update"))
                    .andExpect(jsonPath("$.infoMessage")
                            .value("Changes unsuccessful. Please try again"));
            conn.rollback();
        }
    }

    @Test
    void customerLockContentionReportsFailure_frS0327() throws Exception {
        MockHttpSession session = signon();
        JsonNode details = lookup(session, "00000000001");
        ObjectNode body = validRequest(details);
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT cust_id FROM customers WHERE cust_id = ? FOR UPDATE")) {
                ps.setLong(1, 1L);
                ps.executeQuery();
            }
            mockMvc.perform(put("/api/accounts/1").session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("FAILED"))
                    .andExpect(jsonPath("$.errorMessage")
                            .value("Could not lock customer record for update"));
            conn.rollback();
        }
    }

    @Test
    void concurrentStoreChangeRollsBackToDetails_frS0328() throws Exception {
        MockHttpSession session = signon();
        JsonNode details = lookup(session, "00000000001");
        var account = accountRepository.findById(1L).orElseThrow();
        account.setAcctCurrBal(new BigDecimal("999.99"));
        accountRepository.save(account);
        ObjectNode body = validRequest(details);
        int ficoBefore = customerRepository.findById(1L).orElseThrow()
                .getCustFicoCreditScore();
        mockMvc.perform(put("/api/accounts/1").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DETAILS"))
                .andExpect(jsonPath("$.errorMessage").value(
                        "Record changed by some one else. Please review"));
        // Nothing was written back (SYNCPOINT ROLLBACK).
        assertThat(customerRepository.findById(1L).orElseThrow()
                .getCustFicoCreditScore()).isEqualTo(ficoBefore);
    }

    @Test
    void repeatLookupRereadsCurrentRows_frS0331() throws Exception {
        MockHttpSession session = signon();
        JsonNode details = lookup(session, "00000000001");
        var customer = customerRepository.findById(1L).orElseThrow();
        customer.setCustFicoCreditScore(750);
        customerRepository.save(customer);
        JsonNode again = lookup(session, "00000000001");
        assertThat(again.get("fields").get("ficoScore").asText())
                .isEqualTo("750");
        assertThat(again.get("original").get("ficoScore").asInt())
                .isEqualTo(750);
    }
}
