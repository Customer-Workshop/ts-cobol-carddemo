package com.carddemo;

import com.carddemo.api.CardUpdateCommarea;
import com.carddemo.api.CardUpdateScreen;
import com.carddemo.model.Card;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.SecurityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
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
 * CCRDUPA web surface: the Thymeleaf screen at /cards/update, one POST per
 * AID press echoing the change-action COMMAREA as hidden fields. Test names
 * carry the FR-S06 row they cover.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:cardupdateuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CardUpdateUiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;
    @Autowired private CardRepository cardRepository;

    @BeforeEach
    void seedFileAndUser() {
        SecurityUser user = new SecurityUser();
        user.setUserId("USER0001");
        user.setFirstName("REGULAR");
        user.setLastName("USER");
        user.setPassword("PASSWORD");
        user.setUserType("U");
        userRepository.save(user);

        cardRepository.deleteAll();
        Card card = new Card();
        card.setCardNumber("1111222233334444");
        card.setCardAcctId(1L);
        card.setCardEmbossedName("ADA BYRON");
        card.setCardActiveStatus("Y");
        card.setCardExpirationDate(LocalDate.of(2030, 11, 30));
        card.setCardCvvCode(123);
        cardRepository.save(card);
    }

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "USER0001").param("passwd", "PASSWORD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private CardUpdateScreen screenOf(MvcResult result) {
        return (CardUpdateScreen) result.getModelAndView().getModel().get("screen");
    }

    /** One AID press: re-sends the COMMAREA hidden fields plus map inputs. */
    private ResultActions press(MockHttpSession session, String aid,
                                CardUpdateCommarea commarea,
                                String acct, String card, String name,
                                String status, String month, String year, String day)
            throws Exception {
        MockHttpServletRequestBuilder request = post("/cards/update").session(session)
                .param("aid", aid);
        if (commarea != null) {
            request.param("changeAction", commarea.changeAction());
            if (commarea.accountId() != null) {
                request.param("oldAcctId", commarea.accountId());
            }
            if (commarea.cardNumber() != null) {
                request.param("oldCardNum", commarea.cardNumber());
            }
            if (commarea.embossedName() != null) {
                request.param("oldName", commarea.embossedName());
            }
            if (commarea.activeStatus() != null) {
                request.param("oldStatus", commarea.activeStatus());
            }
            if (commarea.expiryYear() != null) {
                request.param("oldYear", commarea.expiryYear());
            }
            if (commarea.expiryMonth() != null) {
                request.param("oldMonth", commarea.expiryMonth());
            }
            if (commarea.expiryDay() != null) {
                request.param("oldDay", commarea.expiryDay());
            }
        }
        if (acct != null) {
            request.param("acctsid", acct);
        }
        if (card != null) {
            request.param("cardsid", card);
        }
        if (name != null) {
            request.param("crdname", name);
        }
        if (status != null) {
            request.param("crdstcd", status);
        }
        if (month != null) {
            request.param("expmon", month);
        }
        if (year != null) {
            request.param("expyear", year);
        }
        if (day != null) {
            request.param("expday", day);
        }
        return mockMvc.perform(request);
    }

    @Test
    void unauthenticatedAccessBouncesToSignon_frS0601() throws Exception {
        // The EIBCALEN=0 bounce — Spring Security's entry point.
        mockMvc.perform(get("/cards/update"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
    }

    @Test
    void freshGetShowsSearchScreenWithoutRunningEdits_frS0601() throws Exception {
        // :504-509 — first display sends the map; no edits run on entry.
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(get("/cards/update").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("card-update"))
                .andExpect(content().string(containsString("Update Credit Card Details")))
                .andExpect(content().string(containsString("CCUP")))
                .andExpect(content().string(containsString("COCRDUPC")))
                .andReturn();

        CardUpdateScreen screen = screenOf(result);
        assertThat(screen.changeAction()).isEmpty();
        assertThat(screen.infoMessage())
                .isEqualTo("Please enter Account and Card Number");
        assertThat(screen.errorMessage()).isNull();
    }

    @Test
    void unmappedAidActsAsEnterWithNoInvalidKeyMessage_frS0602() throws Exception {
        // :413-424 — F7/PF9/etc run as ENTER; there is no invalid-key path.
        MockHttpSession session = signon();
        MvcResult result = press(session, "F7", CardUpdateCommarea.fresh(),
                        null, null, null, null, null, null, null)
                .andExpect(status().isOk())
                .andExpect(view().name("card-update"))
                .andReturn();

        CardUpdateScreen screen = screenOf(result);
        assertThat(screen.errorMessage()).isEqualTo("No input received");
        assertThat(screen.changeAction()).isEmpty();
    }

    @Test
    void pfThreeTransfersToTheMenu_frS0603() throws Exception {
        // :489-498 + S06-B3 — PF3 exits to the menu from any state.
        MockHttpSession session = signon();
        press(session, "PF3", CardUpdateCommarea.fresh(), null, null,
                        null, null, null, null, null)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void validatedEditShowsConfirmLegendAndPromotesToN_frS0620() throws Exception {
        MockHttpSession session = signon();
        MvcResult fetched = mockMvc.perform(get("/cards/update")
                        .param("acctId", "00000000001")
                        .param("cardNum", "1111222233334444")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        CardUpdateCommarea commarea = screenOf(fetched).commarea();

        MvcResult validated = press(session, "ENTER", commarea,
                        "00000000001", "1111222233334444",
                        "ADA LOVELACE", "Y", "11", "2030", "30")
                .andExpect(status().isOk())
                // bms:158-167 — the bright legend appears only in N.
                .andExpect(content().string(containsString("F5=Save F12=Cancel")))
                .andReturn();

        CardUpdateScreen screen = screenOf(validated);
        assertThat(screen.changeAction()).isEqualTo("N");
        assertThat(screen.confirmPending()).isTrue();
        assertThat(screen.infoMessage())
                .isEqualTo("Changes validated.Press F5 to save");
    }

    @Test
    void enterAfterCommittedResetsToFreshSearch_frS0626() throws Exception {
        // :517-528 — after C the next AID re-initialises the screen.
        MockHttpSession session = signon();
        CardUpdateCommarea committed = new CardUpdateCommarea("C",
                "00000000001", "1111222233334444", "ADA BYRON", "Y",
                "2030", "11", "30");
        MvcResult result = press(session, "ENTER", committed,
                        "00000000001", "1111222233334444",
                        "ADA LOVELACE", "Y", "11", "2030", "30")
                .andExpect(status().isOk())
                .andReturn();

        CardUpdateScreen screen = screenOf(result);
        assertThat(screen.changeAction()).isEmpty();
        assertThat(screen.infoMessage())
                .isEqualTo("Please enter Account and Card Number");
    }

    @Test
    void getWithKeysPreFetchesTheRecord_frS0628() throws Exception {
        // S06-B2 — the COCRDLIC U-select seam lands on GET with the keys;
        // both the plan's acctId/cardNum and the redirect's
        // accountId/cardNumber spellings work.
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(get("/cards/update")
                        .param("accountId", "00000000001")
                        .param("cardNumber", "1111222233334444")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("card-update"))
                .andReturn();

        CardUpdateScreen screen = screenOf(result);
        assertThat(screen.changeAction()).isEqualTo("S");
        assertThat(screen.embossedName()).isEqualTo("ADA BYRON");
        assertThat(screen.expiryDay()).isEqualTo("30");
    }

    @Test
    void fieldsCarryBmsLengthsAndProtectedDay_frS0629() throws Exception {
        // COCRDUP.bms — X(11)/X(16) search keys, X(50) name, X(1) status,
        // 9(2) month, 9(4) year; EXPDAY is DRK PROT (read-only).
        MockHttpSession session = signon();
        mockMvc.perform(get("/cards/update").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "name=\"acctsid\" type=\"text\" maxlength=\"11\"")))
                .andExpect(content().string(containsString(
                        "name=\"cardsid\" type=\"text\" maxlength=\"16\"")))
                .andExpect(content().string(containsString(
                        "name=\"crdname\" type=\"text\" maxlength=\"50\"")))
                .andExpect(content().string(containsString(
                        "name=\"crdstcd\" type=\"text\" maxlength=\"1\"")))
                .andExpect(content().string(containsString(
                        "name=\"expmon\" type=\"text\" maxlength=\"2\"")))
                .andExpect(content().string(containsString(
                        "name=\"expyear\" type=\"text\" maxlength=\"4\"")))
                .andExpect(content().string(containsString(
                        "name=\"expday\" type=\"text\" maxlength=\"2\"")))
                .andExpect(content().string(containsString("id=\"expday\"")));
        // The F5/F12 legend stays dark outside state N.
        mockMvc.perform(get("/cards/update").session(session))
                .andExpect(content().string(not(containsString("F5=Save"))));
    }

    @Test
    void menuOptionFiveDispatchesToCardUpdate_frS0601() throws Exception {
        // The MAIN option 05 entry activates through the UI_ROUTES
        // registry entry added for COCRDUPC.
        MockHttpSession session = signon();
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cards/update"));
    }

    @Test
    void searchOnUiFetchesAndShowsOldImage_frS0610() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = press(session, "ENTER", CardUpdateCommarea.fresh(),
                        "00000000001", "1111222233334444",
                        null, null, null, null, null)
                .andExpect(status().isOk())
                .andExpect(view().name("card-update"))
                .andReturn();

        CardUpdateScreen screen = screenOf(result);
        assertThat(screen.changeAction()).isEqualTo("S");
        assertThat(screen.embossedName()).isEqualTo("ADA BYRON");
        assertThat(screen.infoMessage())
                .isEqualTo("Details of selected card shown above");
    }
}
