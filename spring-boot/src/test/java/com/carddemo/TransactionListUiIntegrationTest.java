package com.carddemo;

import com.carddemo.model.SecurityUser;
import com.carddemo.model.Transaction;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * COTRN00C web surface (wave S-07): the COTRN0A field map, AID dispatch and
 * the client-held paging state (S07-B5) over the real repository. Test names
 * carry the FR-S07 row each covers; cites live in the program FR doc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:tranlistuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TransactionListUiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;

    @BeforeEach
    void seedUserAndTransactions() {
        SecurityUser user = new SecurityUser();
        user.setUserId("USER0001");
        user.setFirstName("REGULAR");
        user.setLastName("USER");
        user.setPassword("PASSWORD");
        user.setUserType("U");
        userRepository.save(user);

        transactionRepository.deleteAllInBatch();
        List<Transaction> rows = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            Transaction value = new Transaction();
            value.setTranId("%016d".formatted(i));
            value.setTranTypeCode("01");
            value.setTranCategoryCode(1);
            value.setTranSource("POS TERM");
            value.setTranDescription("PURCHASE AT STORE NUMBER " + i);
            value.setTranAmount(new BigDecimal("49.50"));
            value.setTranOriginTimestamp(LocalDateTime.of(2025, 3, 4, 1, 2, 3));
            value.setTranProcessTimestamp(LocalDateTime.now());
            rows.add(value);
        }
        transactionRepository.saveAll(rows);
    }

    @Test
    void unsignedNavigationBouncesToSignon_frS0701() throws Exception {
        mockMvc.perform(get("/transactions/list"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
    }

    @Test
    void firstEntryRendersFieldMapAndFirstPage_frS0702_frS0704() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(get("/transactions/list").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-list"))
                .andExpect(content().string(containsString("CT00")))
                .andExpect(content().string(containsString("COTRN00C")))
                .andExpect(content().string(containsString("List Transactions")))
                .andExpect(content().string(containsString("Page: <span>00000001</span>")))
                .andExpect(content().string(containsString("Search Tran ID:")))
                .andExpect(content().string(containsString("Sel")))
                .andExpect(content().string(containsString(" Transaction ID ")))
                .andExpect(content().string(containsString(">0000000000000001<")))
                .andExpect(content().string(containsString(">0000000000000010<")))
                .andExpect(content().string(not(containsString(">0000000000000011<"))))
                .andExpect(content().string(containsString("+00000049.50")))
                .andExpect(content().string(containsString("03/04/25")))
                .andExpect(content().string(
                        containsString("Type 'S' to View Transaction details from the list")))
                .andExpect(content().string(
                        containsString("ENTER=Continue  F3=Back  F7=Backward  F8=Forward")))
                .andExpect(content().string(containsString("name=\"firstId\" value=\"0000000000000001\"")))
                .andExpect(content().string(containsString("name=\"nextPage\" value=\"Y\"")));
    }

    @Test
    void enterWithNumericSearchRepositionsBrowse_frS0705() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/transactions/list").session(session)
                        .param("aid", "ENTER")
                        .param("trnIdIn", "0000000000000012"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-list"))
                .andExpect(model().attribute("message",
                        "You have reached the bottom of the page..."))
                .andExpect(content().string(containsString(">0000000000000012<")))
                .andExpect(content().string(not(containsString(">0000000000000011<"))));
    }

    @Test
    void nonNumericSearchShowsNumericError_frS0706() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/transactions/list").session(session)
                        .param("aid", "ENTER")
                        .param("trnIdIn", "12AB"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-list"))
                .andExpect(model().attribute("message", "Tran ID must be Numeric ..."));
    }

    @Test
    void rowSelectionSNavigatesToView_frS0707() throws Exception {
        // S07-B1: 'S' resolves COTRN01C through the route registry; with the
        // route now registered (S-08) the selection XCTLs to the view with
        // the row's id as the pre-selection.
        MockHttpSession session = signon();
        mockMvc.perform(post("/transactions/list").session(session)
                        .param("aid", "ENTER")
                        .param("sel", "S", "", "", "", "", "", "", "", "", "")
                        .param("trnId", "0000000000000001", "", "", "", "", "", "", "", "", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions/view?tranId=0000000000000001"));
    }

    @Test
    void pf8ThenPf7WalksTheFileWithRoundTrippedState_frS0710_frS0711() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/transactions/list").session(session)
                        .param("aid", "PF8")
                        .param("firstId", "0000000000000001")
                        .param("lastId", "0000000000000010")
                        .param("pageNum", "1").param("nextPage", "Y"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">0000000000000011<")))
                .andExpect(content().string(containsString(">0000000000000015<")))
                .andExpect(content().string(containsString("Page: <span>00000002</span>")))
                .andExpect(model().attribute("message",
                        "You have reached the bottom of the page..."));

        mockMvc.perform(post("/transactions/list").session(session)
                        .param("aid", "PF7")
                        .param("firstId", "0000000000000011")
                        .param("lastId", "0000000000000015")
                        .param("pageNum", "2").param("nextPage", "Y"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">0000000000000001<")))
                .andExpect(content().string(containsString(">0000000000000010<")))
                .andExpect(content().string(containsString("Page: <span>00000001</span>")))
                .andExpect(model().attribute("message",
                        "You have reached the top of the page..."));
    }

    @Test
    void pf3TransfersToMenu_frS0712() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/transactions/list").session(session).param("aid", "PF3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void unmappedAidShowsInvalidKeyMessage_frS0718() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/transactions/list").session(session).param("aid", "F5"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-list"))
                .andExpect(model().attribute("message",
                        "Invalid key pressed. Please see below..."));
    }

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "USER0001").param("passwd", "PASSWORD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
