package com.carddemo;

import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.service.CardViewScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * COCRDSLC web surface (tran CCDL, map CCRDSLA): the screen, its AID keys
 * and the display outcomes, asserting the verbatim strings the FR doc
 * pins. Test names carry the FR-S05 row each covers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        // application.properties pins spring.datasource.url to a fixed mem:db,
        // so this context needs its own URL to isolate its schema.
        "spring.datasource.url=jdbc:h2:mem:cardviewuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CardViewUiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;

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
    void initialRenderShowsPromptAndNoData_frS0501() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(get("/cards/view").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("card-view"))
                .andExpect(model().attribute("message", nullValue()))
                .andExpect(content().string(containsString("CCDL")))
                .andExpect(content().string(containsString("COCRDSLC")))
                .andExpect(content().string(containsString("View Credit Card Detail")))
                .andExpect(content().string(containsString("Account Number    :")))
                .andExpect(content().string(containsString("Card Number       :")))
                .andExpect(content().string(containsString("Please enter Account and Card Number")))
                .andExpect(content().string(containsString("F3=Exit")))
                .andReturn();

        var screen = screen(result);
        assertThat(screen.accountEcho()).isEmpty();
        assertThat(screen.cardEcho()).isEmpty();
        assertThat(screen.inputsProtected()).isFalse();
        assertThat(screen.card()).isNull();
    }

    @Test
    void blankAccountShowsStarRedAndFieldMessage_frS0502() throws Exception {
        MockHttpSession session = signon();
        for (String acct : new String[] {"", "   ", "*"}) {
            MvcResult result = mockMvc.perform(post("/cards/view").session(session)
                            .param("aid", "ENTER").param("accountId", acct)
                            .param("cardNumber", "1111222233334444"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("card-view"))
                    .andExpect(model().attribute("message", "Account number not provided"))
                    .andExpect(content().string(containsString("field-red")))
                    .andReturn();
            var screen = screen(result);
            assertThat(screen.accountEcho()).isEqualTo("*");
            assertThat(screen.accountFieldRed()).isTrue();
            assertThat(screen.cardFieldRed()).isFalse();
            assertThat(screen.cardEcho()).isEqualTo("1111222233334444");
            assertThat(screen.card()).isNull();
        }
    }

    @Test
    void invalidAccountClearedRedWithFilterMessage_frS0503() throws Exception {
        MockHttpSession session = signon();
        for (String acct : new String[] {"123", "1234567890a"}) {
            MvcResult result = mockMvc.perform(post("/cards/view").session(session)
                            .param("aid", "ENTER").param("accountId", acct)
                            .param("cardNumber", "1111222233334444"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("card-view"))
                    .andExpect(model().attribute("message",
                            "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"))
                    .andExpect(content().string(containsString("field-red")))
                    .andReturn();
            var screen = screen(result);
            assertThat(screen.accountEcho()).isEmpty();
            assertThat(screen.accountFieldRed()).isTrue();
            assertThat(screen.cardFieldRed()).isFalse();
            assertThat(screen.card()).isNull();
        }
    }

    @Test
    void blankCardShowsStarRedAndFieldMessage_frS0504() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/cards/view").session(session)
                        .param("aid", "ENTER").param("accountId", "00000000001")
                        .param("cardNumber", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("card-view"))
                .andExpect(model().attribute("message", "Card number not provided"))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.cardEcho()).isEqualTo("*");
        assertThat(screen.cardFieldRed()).isTrue();
        assertThat(screen.accountFieldRed()).isFalse();
        assertThat(screen.cursorField()).isEqualTo("cardsid");
    }

    @Test
    void invalidCardClearedRedWithFilterMessage_frS0505() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/cards/view").session(session)
                        .param("aid", "ENTER").param("accountId", "00000000001")
                        .param("cardNumber", "123"))
                .andExpect(status().isOk())
                .andExpect(view().name("card-view"))
                .andExpect(model().attribute("message",
                        "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.cardEcho()).isEmpty();
        assertThat(screen.cardFieldRed()).isTrue();
        assertThat(screen.accountFieldRed()).isFalse();
        assertThat(screen.cursorField()).isEqualTo("cardsid");
    }

    @Test
    void bothBlankIsNoInputReceived_frS0506() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/cards/view").session(session)
                        .param("aid", "ENTER"))
                .andExpect(status().isOk())
                .andExpect(view().name("card-view"))
                .andExpect(model().attribute("message", "No input received"))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.accountEcho()).isEqualTo("*");
        assertThat(screen.cardEcho()).isEqualTo("*");
        assertThat(screen.accountFieldRed()).isTrue();
        assertThat(screen.cardFieldRed()).isTrue();
    }

    @Test
    void seededCardRendersDetailFields_frS0509() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/cards/view").session(session)
                        .param("aid", "ENTER").param("accountId", "00000000001")
                        .param("cardNumber", "1111222233334444"))
                .andExpect(status().isOk())
                .andExpect(view().name("card-view"))
                .andExpect(model().attribute("message", nullValue()))
                .andExpect(content().string(containsString("   Displaying requested details")))
                .andExpect(content().string(containsString("Ada Byron")))
                .andExpect(content().string(containsString("id=\"crdstcd\">Y")))
                .andExpect(content().string(containsString("id=\"expmon\">01")))
                .andExpect(content().string(containsString("id=\"expyear\">2025")))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.accountFieldRed()).isFalse();
        assertThat(screen.cardFieldRed()).isFalse();
        assertThat(screen.card()).isNotNull();
        assertThat(screen.card().expiryMonth()).isEqualTo("01");
        assertThat(screen.card().expiryYear()).isEqualTo("2025");
        assertThat(screen.card().activeStatus()).isEqualTo("Y");
    }

    @Test
    void unknownCardFlagsBothAndKeepsEchoes_frS0510() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/cards/view").session(session)
                        .param("aid", "ENTER").param("accountId", "00000000001")
                        .param("cardNumber", "9999999999999999"))
                .andExpect(status().isOk())
                .andExpect(view().name("card-view"))
                .andExpect(model().attribute("message",
                        "Did not find cards for this search condition"))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.accountFieldRed()).isTrue();
        assertThat(screen.cardFieldRed()).isTrue();
        assertThat(screen.accountEcho()).isEqualTo("00000000001");
        assertThat(screen.cardEcho()).isEqualTo("9999999999999999");
        assertThat(screen.card()).isNull();
    }

    @Test
    void foreignAccountStillDisplaysCard_frS0512() throws Exception {
        // The keyed read uses the card number alone: account 2's entry
        // still resolves account 1's card (cbl:739-750, source defect).
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/cards/view").session(session)
                        .param("aid", "ENTER").param("accountId", "00000000002")
                        .param("cardNumber", "1111222233334444"))
                .andExpect(status().isOk())
                .andExpect(view().name("card-view"))
                .andExpect(model().attribute("message", nullValue()))
                .andExpect(content().string(containsString("Ada Byron")))
                .andReturn();
        assertThat(screen(result).card()).isNotNull();
    }

    @Test
    void cardListContextAutoReadsAndProtects_frS0513() throws Exception {
        // S05-B2: both keys on GET are the card-list XCTL — the edits are
        // skipped, the read runs at once, the inputs render protected and
        // the keys echo zero-padded (9(11)/9(16) numerics).
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(get("/cards/view").session(session)
                        .param("accountId", "1")
                        .param("cardNumber", "1111222233334444"))
                .andExpect(status().isOk())
                .andExpect(view().name("card-view"))
                .andExpect(model().attribute("message", nullValue()))
                .andExpect(model().attribute("returnUrl", "/cards/list"))
                .andExpect(content().string(containsString("Ada Byron")))
                .andExpect(content().string(containsString("id=\"acctsid\" name=\"accountId\" type=\"text\" maxlength=\"11\" size=\"11\"")))
                .andExpect(content().string(containsString("readonly")))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.inputsProtected()).isTrue();
        assertThat(screen.accountEcho()).isEqualTo("00000000001");
        assertThat(screen.cardEcho()).isEqualTo("1111222233334444");

        // A partial hand-off (one key only) is not card-list context: the
        // initial display with the fixed prompt renders instead.
        mockMvc.perform(get("/cards/view").session(session)
                        .param("accountId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Please enter Account and Card Number")));
    }

    @Test
    void pf3ReturnsToCallerOrMenu_frS0514() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/cards/view").session(session).param("aid", "PF3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
        mockMvc.perform(post("/cards/view").session(session)
                        .param("aid", "PF3").param("returnUrl", "/cards/list"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cards/list"));
        // Internal paths only — an external URL cannot redirect off-site.
        mockMvc.perform(post("/cards/view").session(session)
                        .param("aid", "PF3").param("returnUrl", "https://off-site.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));

        // From the card-list context, PF3 follows the resolved caller
        // (COCRDLIC's route) — CDEMO-FROM-PROGRAM.
        mockMvc.perform(post("/cards/view").session(session)
                        .param("aid", "PF3")
                        .param("accountId", "1").param("cardNumber", "1111222233334444")
                        .param("cardListContext", "true").param("returnUrl", "/cards/list"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cards/list"));
    }

    @Test
    void unmappedAidSubmitsAsEnterWithNoInvalidKeyText_frS0515() throws Exception {
        // :291-299 — every AID except PF3 is forced to ENTER; the screen
        // has no invalid-key message.
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/cards/view").session(session)
                        .param("aid", "F7").param("accountId", "00000000001")
                        .param("cardNumber", "1111222233334444"))
                .andExpect(status().isOk())
                .andExpect(view().name("card-view"))
                .andExpect(content().string(not(containsString("Invalid key"))))
                .andReturn();
        assertThat(screen(result).card()).isNotNull();
    }

    @Test
    void menuOptionFourNavigatesToCardView_frS0513() throws Exception {
        // S05-B2 inbound edge: the S-01 route registry now resolves
        // COCRDSLC (menu option 4) to this screen.
        MockHttpSession session = signon();
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cards/view"));
    }

    @Test
    void inputLengthsMatchTheMap_frS0516() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(get("/cards/view").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("maxlength=\"11\"")))
                .andExpect(content().string(containsString("maxlength=\"16\"")))
                .andExpect(content().string(containsString("ENTER=Search Cards  F3=Exit")));
    }

    @Test
    void unsignedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/cards/view"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        mockMvc.perform(post("/cards/view").param("accountId", "00000000001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        mockMvc.perform(get("/api/cards/1111222233334444"))
                .andExpect(status().isUnauthorized());
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
