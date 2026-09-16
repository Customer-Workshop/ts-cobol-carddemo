package com.carddemo;

import com.carddemo.api.CardListPageState;
import com.carddemo.api.CardListRow;
import com.carddemo.api.CardListScreenView;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * CCRDLIA web surface: the Thymeleaf screen at /cards/list, one POST per AID
 * press echoing the paging COMMAREA as hidden fields. Test names carry the
 * FR-S04 row they cover.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:cardlistuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CardListUiIntegrationTest {

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
        for (int i = 1; i <= 10; i++) {
            Card card = new Card();
            card.setCardNumber("%016d".formatted(i));
            card.setCardAcctId(i <= 7 ? 1L : 2L);
            card.setCardActiveStatus("Y");
            cardRepository.save(card);
        }
    }

    private static String key(int i) {
        return "%016d".formatted(i);
    }

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "USER0001").param("passwd", "PASSWORD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private CardListPageState stateOf(MvcResult result) {
        return (CardListPageState) result.getModelAndView().getModel().get("pageState");
    }

    private CardListScreenView screenOf(MvcResult result) {
        return (CardListScreenView) result.getModelAndView().getModel().get("screen");
    }

    private MvcResult page(MockHttpSession session) throws Exception {
        return mockMvc.perform(get("/cards/list").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("card-list"))
                .andReturn();
    }

    /** One AID press: re-sends the COMMAREA hidden fields plus screen inputs. */
    private ResultActions press(MockHttpSession session, String aid, CardListPageState state,
                                String accountFilter, String cardFilter, String... selections)
            throws Exception {
        MockHttpServletRequestBuilder request = post("/cards/list").session(session)
                .param("aid", aid)
                .param("screenNumber", String.valueOf(state.screenNumber()))
                .param("lastPageShown", String.valueOf(state.lastPageShown()))
                .param("nextPageExists", String.valueOf(state.nextPageExists()));
        if (state.firstCardNumber() != null) {
            request.param("firstCardNumber", state.firstCardNumber());
        }
        if (state.lastCardNumber() != null) {
            request.param("lastCardNumber", state.lastCardNumber());
        }
        String[] accounts = new String[CardListPageState.ROW_COUNT];
        String[] cards = new String[CardListPageState.ROW_COUNT];
        String[] statuses = new String[CardListPageState.ROW_COUNT];
        for (int i = 0; i < CardListPageState.ROW_COUNT; i++) {
            CardListRow row = state.rows().get(i);
            accounts[i] = row == null || row.accountId() == null ? "" : row.accountId().toString();
            cards[i] = row == null || row.cardNumber() == null ? "" : row.cardNumber();
            statuses[i] = row == null || row.activeStatus() == null ? "" : row.activeStatus();
        }
        request.param("rowAcct", accounts)
                .param("rowCard", cards)
                .param("rowStatus", statuses)
                .param("crdsel", selections == null || selections.length == 0
                        ? new String[] {"", "", "", "", "", "", ""} : selections);
        if (accountFilter != null) {
            request.param("acctsid", accountFilter);
        }
        if (cardFilter != null) {
            request.param("cardsid", cardFilter);
        }
        return mockMvc.perform(request);
    }

    @Test
    void entryRendersCobolFieldMapAndHiddenCommarea_frS0401() throws Exception {
        MockHttpSession session = signon();
        CardListPageState state = stateOf(page(session));
        assertThat(state.screenNumber()).isEqualTo(1);
        assertThat(state.firstCardNumber()).isEqualTo(key(1));
        assertThat(state.lastCardNumber()).isEqualTo(key(8));
        assertThat(state.nextPageExists()).isTrue();

        mockMvc.perform(get("/cards/list").session(session))
                .andExpect(content().string(containsString("COCRDLIC")))
                .andExpect(content().string(containsString("CCLI")))
                .andExpect(content().string(containsString("List Credit Cards")))
                .andExpect(content().string(containsString("Account Number    :")))
                .andExpect(content().string(containsString("Credit Card Number:")))
                .andExpect(content().string(containsString(key(1))))
                .andExpect(content().string(containsString(
                        "  F3=Exit F7=Backward  F8=Forward")))
                .andExpect(content().string(containsString("name=\"firstCardNumber\"")))
                .andExpect(content().string(containsString("name=\"lastCardNumber\"")))
                .andExpect(content().string(containsString("name=\"screenNumber\"")))
                .andExpect(content().string(containsString(
                        "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD")));
    }

    @Test
    void pfSevenOnFirstPageShowsNoPreviousPages_frS0402() throws Exception {
        MockHttpSession session = signon();
        CardListPageState state = stateOf(page(session));
        press(session, "PF7", state, null, null)
                .andExpect(status().isOk())
                .andExpect(view().name("card-list"))
                .andExpect(content().string(containsString("NO PREVIOUS PAGES TO DISPLAY")));
    }

    @Test
    void pfEightAdvancesToSecondPage_frS0403() throws Exception {
        MockHttpSession session = signon();
        CardListPageState state = stateOf(page(session));
        MvcResult result = press(session, "PF8", state, null, null)
                .andExpect(status().isOk())
                .andExpect(view().name("card-list"))
                .andExpect(content().string(containsString(key(8))))
                .andExpect(content().string(containsString(key(10))))
                .andReturn();
        assertThat(screenOf(result).screenNumber()).isEqualTo(2);
        assertThat(stateOf(result).firstCardNumber()).isEqualTo(key(8));
    }

    @Test
    void unmappedAidRelistsThePageLikeEnter_frS0406_s04B3() throws Exception {
        MockHttpSession session = signon();
        CardListPageState state = stateOf(page(session));
        MvcResult f9 = press(session, "F9", state, null, null)
                .andExpect(status().isOk())
                .andExpect(view().name("card-list"))
                .andReturn();
        assertThat(screenOf(f9).rows().get(0).cardNumber()).isEqualTo(key(1));
        assertThat(screenOf(f9).errorMessage()).isNull();
    }

    @Test
    void invalidFilterEchoesAndProtectsEverySelect_frS0407_frS0411() throws Exception {
        MockHttpSession session = signon();
        CardListPageState state = stateOf(page(session));
        MvcResult result = press(session, "ENTER", state, "ABCDEFGHIJK", null)
                .andExpect(status().isOk())
                .andExpect(view().name("card-list"))
                .andExpect(content().string(containsString(
                        "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER")))
                .andExpect(content().string(containsString("ABCDEFGHIJK")))
                .andReturn();
        CardListScreenView screen = screenOf(result);
        assertThat(screen.accountFilterInvalid()).isTrue();
        assertThat(screen.cursorField()).isEqualTo("acctsid");
        assertThat(screen.rows()).allSatisfy(row -> assertThat(row.selectProtected()).isTrue());
        // Same rows as before — a filter error never re-reads the file.
        assertThat(screen.rows().get(0).cardNumber()).isEqualTo(key(1));
    }

    @Test
    void cardFilterReturnsTheExactRow_frS0409() throws Exception {
        MockHttpSession session = signon();
        CardListPageState state = stateOf(page(session));
        MvcResult result = press(session, "ENTER", state, null, key(5))
                .andExpect(status().isOk())
                .andReturn();
        CardListScreenView screen = screenOf(result);
        assertThat(screen.rows().get(0).cardNumber()).isEqualTo(key(5));
        assertThat(screen.rows().subList(1, 7))
                .allSatisfy(row -> assertThat(row.cardNumber()).isNull());
    }

    @Test
    void blankFiltersRenderFullBrowseWithOpenSelects_frS0410() throws Exception {
        MockHttpSession session = signon();
        CardListPageState state = stateOf(page(session));
        MvcResult result = press(session, "ENTER", state, "", "")
                .andExpect(status().isOk())
                .andReturn();
        CardListScreenView screen = screenOf(result);
        assertThat(screen.rows().stream().map(r -> r.cardNumber()).toList())
                .containsExactly(key(1), key(2), key(3), key(4), key(5), key(6), key(7));
        assertThat(screen.rows()).allSatisfy(row -> assertThat(row.selectProtected()).isFalse());
    }

    @Test
    void multipleSelectsShowOnlyOneMessageAndRebrowse_frS0412_s04B2() throws Exception {
        MockHttpSession session = signon();
        CardListPageState state = stateOf(page(session));
        state = stateOf(press(session, "PF8", state, null, null).andReturn());
        MvcResult result = press(session, "ENTER", state, null, null,
                "S", "", "U", "", "", "", "")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE")))
                .andReturn();
        CardListScreenView screen = screenOf(result);
        // S04-B2 — re-browse from file start while the page number stays.
        assertThat(screen.rows().get(0).cardNumber()).isEqualTo(key(1));
        assertThat(screen.screenNumber()).isEqualTo(2);
        assertThat(screen.rows().get(0).select()).isEqualTo("S");
        assertThat(screen.rows().get(2).select()).isEqualTo("U");
    }

    @Test
    void invalidSelectCodeShowsActionError_frS0413() throws Exception {
        MockHttpSession session = signon();
        CardListPageState state = stateOf(page(session));
        MvcResult result = press(session, "ENTER", state, null, null,
                "", "", "X", "", "", "", "")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INVALID ACTION CODE")))
                .andReturn();
        assertThat(screenOf(result).rows().get(2).selectError()).isTrue();
    }

    @Test
    void selectionNavigatesThroughRegistryOrShowsComingSoon_s04B1() throws Exception {
        MockHttpSession session = signon();
        CardListPageState state = stateOf(page(session));

        // COCRDSLC has no UI route yet — the coming-soon idiom, not a dead
        // link. The message emits the option name DELIMITED BY SPACE
        // (COMEN01C.cbl:172-176), so only the first word reaches the screen.
        press(session, "ENTER", state, null, null, "S", "", "", "", "", "", "")
                .andExpect(status().isOk())
                .andExpect(view().name("card-list"))
                .andExpect(model().attribute("messageStyle", "info"))
                .andExpect(model().attribute("message",
                        "This option Creditis coming soon ..."));

        press(session, "ENTER", state, null, null, "", "", "U", "", "", "", "")
                .andExpect(status().isOk())
                .andExpect(view().name("card-list"))
                .andExpect(model().attribute("message",
                        "This option Creditis coming soon ..."));
    }

    @Test
    void pfSevenReturnsToPreviousPage_frS0416() throws Exception {
        MockHttpSession session = signon();
        CardListPageState state = stateOf(page(session));
        state = stateOf(press(session, "PF8", state, null, null).andReturn());
        MvcResult result = press(session, "PF7", state, null, null)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(key(1))))
                .andReturn();
        assertThat(screenOf(result).screenNumber()).isEqualTo(1);
        assertThat(stateOf(result).firstCardNumber()).isEqualTo(key(1));
    }

    @Test
    void pageNumberWrapsPastNine_frS0417() throws Exception {
        MockHttpSession session = signon();
        // ADD 1 on PIC 9(1) wraps 9→0 (:492); only an empty read leaves the
        // wrapped 0 — a filled row rescues it back to 1 (:1177-1181).
        CardListPageState crafted = new CardListPageState(
                key(8), "9999999999999999", 9, false, true,
                stateOf(page(session)).rows());
        MvcResult result = press(session, "PF8", crafted, null, null)
                .andExpect(status().isOk())
                .andReturn();
        assertThat(screenOf(result).screenNumber()).isEqualTo(0);
    }

    @Test
    void hiddenCommareaFieldsCarryThePageState_frS0418() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = press(session, "PF8", stateOf(page(session)), null, null)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"screenNumber\" value=\"2\"")))
                .andExpect(content().string(containsString(
                        "name=\"firstCardNumber\" value=\"" + key(8) + "\"")))
                .andReturn();
        CardListPageState state = stateOf(result);
        assertThat(state.rows().get(0).cardNumber()).isEqualTo(key(8));
        assertThat(state.rows().get(2).cardNumber()).isEqualTo(key(10));
    }

    @Test
    void infoAndErrorLinesFollowCobolRules_frS0419() throws Exception {
        MockHttpSession session = signon();
        CardListPageState state = stateOf(page(session));
        MvcResult normal = press(session, "ENTER", state, null, null).andReturn();
        assertThat(screenOf(normal).infoMessage())
                .isEqualTo("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD");

        MvcResult pf7 = press(session, "PF7", state, null, null).andReturn();
        assertThat(screenOf(pf7).infoMessage()).isNull();
        assertThat(screenOf(pf7).errorMessage()).isEqualTo("NO PREVIOUS PAGES TO DISPLAY");
    }

    @Test
    void backwardExhaustionRendersFileErrorLayout_frS0421_s04B4() throws Exception {
        MockHttpSession session = signon();
        // S04-B2 path: selection error on page 2 re-browses from file start
        // keeping the page number, then PF7 hits BOF with no prior records.
        CardListPageState state = stateOf(press(session, "PF8",
                stateOf(page(session)), null, null).andReturn());
        state = stateOf(press(session, "ENTER", state, null, null,
                "", "", "", "", "", "", "X").andReturn());
        assertThat(state.firstCardNumber()).isEqualTo(key(1));

        // Landing above page 1 keeps the file error (:1361-1369); page 3 with
        // a file-start anchor hits BOF immediately.
        mockMvc.perform(post("/cards/list").session(session)
                        .param("aid", "PF7")
                        .param("firstCardNumber", state.firstCardNumber())
                        .param("lastCardNumber", state.lastCardNumber() == null
                                ? "" : state.lastCardNumber())
                        .param("screenNumber", "3")
                        .param("lastPageShown", String.valueOf(state.lastPageShown()))
                        .param("nextPageExists", String.valueOf(state.nextPageExists())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "File Error: READ     on CARDDAT   "
                                + "returned RESP 000000020 ,RESP2 000000090")));
    }

    @Test
    void pfThreeReturnsToMenu_frS0422() throws Exception {
        MockHttpSession session = signon();
        press(session, "PF3", stateOf(page(session)), null, null)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void unsignedAccessBouncesToSignon() throws Exception {
        mockMvc.perform(get("/cards/list"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        mockMvc.perform(post("/cards/list").param("aid", "ENTER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
    }
}
