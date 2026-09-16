package com.carddemo;

import com.carddemo.model.SecurityUser;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.service.CardViewScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * FR-S05-11 / S05-B4: a store failure on the keyed read renders the fixed
 * WS-FILE-ERROR-MESSAGE layout (COCRDSLC.cbl:762-771) — RESP 000000017 /
 * RESP2 000000120 — with only the account field flagged. Kept in its own
 * context so the repository double cannot leak into the real-data tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:cardviewstoretest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CardViewUiStoreErrorIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;
    @MockitoBean private CardRepository cardRepository;

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
    void cardStoreErrorRendersFileErrorLayout_frS0511() throws Exception {
        Mockito.when(cardRepository.findById(ArgumentMatchers.anyString()))
                .thenThrow(new DataAccessResourceFailureException("store unavailable"));
        MockHttpSession session = signon();

        MvcResult result = mockMvc.perform(post("/cards/view").session(session)
                        .param("aid", "ENTER").param("accountId", "00000000001")
                        .param("cardNumber", "1111222233334444"))
                .andExpect(status().isOk())
                .andExpect(view().name("card-view"))
                .andExpect(model().attribute("message",
                        "File Error: READ     on CARDDAT   returned RESP 000000017 ,RESP2 000000120 "))
                .andReturn();
        var screen = screen(result);
        // :762-771 — only the account flag is forced; the entered values
        // stay because the COMMAREA keys were not zeroed on this branch.
        assertThat(screen.accountFieldRed()).isTrue();
        assertThat(screen.cardFieldRed()).isFalse();
        assertThat(screen.accountEcho()).isEqualTo("00000000001");
        assertThat(screen.cardEcho()).isEqualTo("1111222233334444");
        assertThat(screen.card()).isNull();
    }

    private CardViewScreen screen(MvcResult result) {
        return (CardViewScreen) result.getModelAndView().getModel().get("screen");
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
