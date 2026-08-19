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
        stale.put("originalEmbossedName", "Someone Else");
        mockMvc.perform(put("/api/cards/1111222233334444").session(session)
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
        update.put("expectedCurrentBalance", "999999.99");
        mockMvc.perform(put("/api/accounts").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(update)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Record changed by some one else. Please review"));
    }

    private ObjectNode cardUpdate(JsonNode detail) {
        ObjectNode request = objectMapper.createObjectNode();
        String expirationDate = detail.get("expirationDate").asText();
        request.put("accountId", detail.get("accountId").asText());
        request.put("cardNumber", detail.get("cardNumber").asText());
        request.put("embossedName", "Ada Byron Updated");
        request.put("activeStatus", detail.get("activeStatus").asText());
        request.put("expiryMonth", Integer.parseInt(expirationDate.substring(5, 7)));
        request.put("expiryYear", Integer.parseInt(expirationDate.substring(0, 4)));
        request.put("originalEmbossedName", detail.get("embossedName").asText());
        request.put("originalActiveStatus", detail.get("activeStatus").asText());
        request.put("originalExpiryMonth", Integer.parseInt(expirationDate.substring(5, 7)));
        request.put("originalExpiryYear", Integer.parseInt(expirationDate.substring(0, 4)));
        return request;
    }

    private ObjectNode accountUpdate(JsonNode view) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("accountId", view.get("accountId").asText());
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
        request.put("ficoScore", view.get("ficoScore").asInt());
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
