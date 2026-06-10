package com.carddemo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAccount_returnsAccountDetails() throws Exception {
        mockMvc.perform(get("/api/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.acctId").value(1))
                .andExpect(jsonPath("$.account.acctActiveStatus").value("Y"))
                .andExpect(jsonPath("$.account.acctCurrBal").value(1500.00))
                .andExpect(jsonPath("$.account.acctCreditLimit").value(5000.00));
    }

    @Test
    void getAccount_returns404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/accounts/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void updateAccount_updatesFields() throws Exception {
        mockMvc.perform(put("/api/accounts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acctCreditLimit\": 6000.00, \"acctActiveStatus\": \"Y\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acctCreditLimit").value(6000.00));
    }

    @Test
    void updateAccount_rejectsInvalidStatus() throws Exception {
        mockMvc.perform(put("/api/accounts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acctActiveStatus\": \"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void authorize_approvesValidTransaction() throws Exception {
        mockMvc.perform(post("/api/accounts/1/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionAmount\": 1000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.responseCode").value("00"));
    }

    @Test
    void authorize_declinesOverlimitTransaction() throws Exception {
        // Account 4 has bal=4999, limit=5000 → available=1
        mockMvc.perform(post("/api/accounts/4/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionAmount\": 100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.responseCode").value("05"));
    }

    @Test
    void authorize_returnsBadRequestWithoutAmount() throws Exception {
        mockMvc.perform(post("/api/accounts/1/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
