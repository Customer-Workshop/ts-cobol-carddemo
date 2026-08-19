package com.carddemo;

import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ApiIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;
    @Autowired private ObjectMapper objectMapper;

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

    @Test
    void signonAndSessionSupportAdminAndRegularUsers() throws Exception {
        MockHttpSession adminSession = signon("admin001", "password", "/api/admin/menu");
        mockMvc.perform(get("/api/auth/session").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("ADMIN001"))
                .andExpect(jsonPath("$.userType").value("A"));

        MockHttpSession userSession = signon("user0001", "password", "/api/menu");
        mockMvc.perform(get("/api/menu").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(11));
        mockMvc.perform(get("/api/admin/menu").session(userSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void signonPreservesCobolValidationOrderAndMessages() throws Exception {
        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter User ID ..."));
        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"ADMIN001\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter Password ..."));
        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"ADMIN001\",\"password\":\"WRONG\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong Password. Try again ..."));
    }

    @Test
    void menuSelectionAndAccountViewUseCobolTargetsAndValues() throws Exception {
        MockHttpSession session = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        mockMvc.perform(post("/api/menu/select").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program").value("COACTVWC"))
                .andExpect(jsonPath("$.implemented").value(true));
        mockMvc.perform(post("/api/menu/select").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"11\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.implemented").value(false))
                .andExpect(jsonPath("$.message").value(containsString("not installed")));

        mockMvc.perform(get("/api/accounts/00000000001").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance").value(194.00))
                .andExpect(jsonPath("$.ssn").value("123-45-6789"));
        mockMvc.perform(get("/api/accounts/1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1));
        mockMvc.perform(get("/api/accounts/00000000002").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Cross ref file")));
    }

    @Test
    void cardListAndDetailExposeCobolSelectionSemantics() throws Exception {
        MockHttpSession session = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        mockMvc.perform(get("/api/cards").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(7))
                .andExpect(jsonPath("$.cards[0].selectionViewCode").value("S"))
                .andExpect(jsonPath("$.cards[0].selectionUpdateCode").value("U"))
                .andExpect(jsonPath("$.cards[0].cardNumber").value("1111222233334444"));
        mockMvc.perform(get("/api/cards/1111222233334444").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.cvvCode").value("123"))
                .andExpect(jsonPath("$.embossedName").value(containsString("Byron")));
        mockMvc.perform(get("/api/cards").param("cardNumber", "123")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"));
        mockMvc.perform(get("/api/cards").param("page", "1").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void cardUpdateValidatesAndRejectsConcurrentChange() throws Exception {
        MockHttpSession session = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        JsonNode detail = objectMapper.readTree(mockMvc.perform(
                        get("/api/cards/1111222233334444").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ObjectNode stale = cardUpdate(detail);
        ((ObjectNode) stale.get("original")).put("embossedName", "Someone Else");
        mockMvc.perform(put("/api/cards/1111222233334444").param("accountId", "1").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(stale)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Record changed by some one else. Please review"));
    }

    @Test
    void accountUpdateRejectsConcurrentChange() throws Exception {
        MockHttpSession session = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        JsonNode view = objectMapper.readTree(mockMvc.perform(
                        get("/api/accounts/1").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ObjectNode update = accountUpdate(view);
        ((ObjectNode) update.get("original")).put("currentBalance", "999999.99");
        mockMvc.perform(put("/api/accounts/1").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(update)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Record changed by some one else. Please review"));
    }

    @Test
    void updateProgramsRequireCompleteOriginalSnapshots() throws Exception {
        MockHttpSession session = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        mockMvc.perform(put("/api/cards/1111222233334444").param("accountId", "1")
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"embossedName":"Ada Byron","activeStatus":"Y",
                                 "expiryMonth":1,"expiryYear":2025}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Original values must be supplied for update."));
        JsonNode view = objectMapper.readTree(mockMvc.perform(
                        get("/api/accounts/1").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ObjectNode request = accountUpdate(view);
        request.remove("original");
        mockMvc.perform(put("/api/accounts/1").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Original values must be supplied for update."));
    }

    @Test
    void transactionsBillingReportsAndAdminUsersUsePortedFlows() throws Exception {
        MockHttpSession admin = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        MockHttpSession regular = signon("USER0001", "PASSWORD", "/api/menu");
        mockMvc.perform(get("/api/transactions").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.transactions[0].transactionCategoryCode").value("0001"));
        mockMvc.perform(get("/api/transactions/0000000000000001").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCategoryCode").value("0001"));
        mockMvc.perform(post("/api/transactions").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"cardNumber":"1111222233334444","transactionTypeCode":"01",
                                 "transactionCategoryCode":"0001","source":"POS TERM",
                                 "description":"Test add","amount":12.34,
                                 "originDate":"2024-01-01","processDate":"2024-01-01",
                                 "merchantId":123,"merchantName":"Merchant",
                                 "merchantCity":"Boston","merchantZip":"02108",
                                 "confirmation":"Y"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCategoryCode").value("0001"));
        mockMvc.perform(post("/api/billing/payments").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"1\",\"confirmation\":\"N\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confirm to make a bill payment..."));
        mockMvc.perform(post("/api/reports").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"reportName":"Monthly","startDate":"2024-01-01",
                                 "endDate":"2024-01-31","confirmation":"Y"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted; Spring Batch launch pending"));
        mockMvc.perform(get("/api/admin/users").session(regular))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"userId":"newuser1","firstName":"New","lastName":"User",
                                 "password":"PASSWORD","userType":"U"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("NEWUSER1"));
    }

    @Test
    void adminCreatedPasswordsUseSignonNormalization() throws Exception {
        MockHttpSession admin = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"userId":"lower01","firstName":"Lower","lastName":"Case",
                                 "password":"lowerpas","userType":"U"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/signoff").session(admin))
                .andExpect(status().isOk());

        signon("LOWER01", "lowerpas", "/api/menu");
    }

    @Test
    void h2ConsoleIsNotUnauthenticatedByDefault() throws Exception {
        mockMvc.perform(get("/h2-console/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidReportJobLaunchIsReturnedAsBadRequest() throws Exception {
        MockHttpSession admin = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        mockMvc.perform(post("/api/admin/jobs/cbtrn03Job").session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "cbtrn03Job requires non-blank startDate and endDate parameters"));
    }

    private ObjectNode cardUpdate(JsonNode detail) {
        ObjectNode request = objectMapper.createObjectNode();
        String expirationDate = detail.get("expirationDate").asText();
        request.put("embossedName", "Ada Byron Updated");
        request.put("activeStatus", detail.get("activeStatus").asText());
        request.put("expiryMonth", Integer.parseInt(expirationDate.substring(5, 7)));
        request.put("expiryYear", Integer.parseInt(expirationDate.substring(0, 4)));
        ObjectNode original = objectMapper.createObjectNode();
        original.put("embossedName", detail.get("embossedName").asText());
        original.put("activeStatus", detail.get("activeStatus").asText());
        original.put("expiryMonth", Integer.parseInt(expirationDate.substring(5, 7)));
        original.put("expiryYear", Integer.parseInt(expirationDate.substring(0, 4)));
        request.set("original", original);
        return request;
    }

    private ObjectNode accountUpdate(JsonNode view) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("activeStatus", view.get("activeStatus").asText());
        request.put("currentBalance", view.get("currentBalance").asText());
        request.put("creditLimit", view.get("creditLimit").asText());
        request.put("cashCreditLimit", view.get("cashCreditLimit").asText());
        request.put("openDate", view.get("openDate").asText());
        request.put("expirationDate", view.get("expirationDate").asText());
        request.put("reissueDate", view.get("reissueDate").asText());
        request.put("currentCycleCredit", view.get("currentCycleCredit").asText());
        request.put("currentCycleDebit", view.get("currentCycleDebit").asText());
        request.put("accountGroup", view.get("accountGroup").asText());
        request.put("customerId", view.get("customerId").asLong());
        request.put("ssn", view.get("ssn").asText());
        request.put("dateOfBirth", view.get("dateOfBirth").asText());
        request.put("ficoScore", 300);
        request.put("firstName", view.get("firstName").asText());
        request.put("middleName", view.get("middleName").asText());
        request.put("lastName", view.get("lastName").asText());
        request.put("addressLine1", view.get("addressLine1").asText());
        request.put("addressLine2", view.get("addressLine2").asText());
        request.put("addressLine3", view.get("addressLine3").asText());
        request.put("stateCode", view.get("stateCode").asText());
        request.put("zip", view.get("zip").asText());
        request.put("countryCode", view.get("countryCode").asText());
        request.put("phoneNumber1", "2125550100");
        request.put("phoneNumber2", "2125550101");
        request.put("governmentIssuedId", view.get("governmentIssuedId").asText());
        request.put("eftAccountId", "1234567890");
        request.put("primaryCardHolderIndicator", view.get("primaryCardHolderIndicator").asText());
        ObjectNode original = objectMapper.createObjectNode();
        original.put("activeStatus", view.get("activeStatus").asText());
        original.put("currentBalance", view.get("currentBalance").asText());
        original.put("creditLimit", view.get("creditLimit").asText());
        original.put("cashCreditLimit", view.get("cashCreditLimit").asText());
        original.put("openDate", view.get("openDate").asText());
        original.put("expirationDate", view.get("expirationDate").asText());
        original.put("reissueDate", view.get("reissueDate").asText());
        original.put("currentCycleCredit", view.get("currentCycleCredit").asText());
        original.put("currentCycleDebit", view.get("currentCycleDebit").asText());
        original.put("accountGroup", view.get("accountGroup").asText());
        original.put("customerId", view.get("customerId").asLong());
        original.put("ssn", view.get("ssn").asText());
        original.put("dateOfBirth", view.get("dateOfBirth").asText());
        original.put("ficoScore", view.get("ficoScore").asInt());
        original.put("firstName", view.get("firstName").asText());
        original.put("middleName", view.get("middleName").asText());
        original.put("lastName", view.get("lastName").asText());
        original.put("addressLine1", view.get("addressLine1").asText());
        original.put("addressLine2", view.get("addressLine2").asText());
        original.put("addressLine3", view.get("addressLine3").asText());
        original.put("stateCode", view.get("stateCode").asText());
        original.put("zip", view.get("zip").asText());
        original.put("countryCode", view.get("countryCode").asText());
        original.put("phoneNumber1", view.get("phoneNumber1").asText());
        original.put("phoneNumber2", view.get("phoneNumber2").asText());
        original.put("governmentIssuedId", view.get("governmentIssuedId").asText());
        original.put("eftAccountId", view.get("eftAccountId").asText());
        original.put("primaryCardHolderIndicator", view.get("primaryCardHolderIndicator").asText());
        request.set("original", original);
        return request;
    }

    private MockHttpSession signon(String userId, String password, String landing) throws Exception {
        var result = mockMvc.perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landingTarget").value(landing))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
