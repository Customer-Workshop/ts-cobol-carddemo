package com.carddemo;

import com.carddemo.model.Card;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.CardRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COCRDLIC over the REST surface: GET /api/cards is the fresh entry and
 * POST /api/cards/list is one call per AID press, each echoing the paging
 * COMMAREA as pageState. Test names carry the FR-S04 row they cover.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:cardlistit;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CardListIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
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
        MvcResult result = mockMvc.perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"USER0001\",\"password\":\"PASSWORD\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** One AID press: wraps the echoed pageState back into the request. */
    private JsonNode press(MockHttpSession session, String aid, JsonNode pageState)
            throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("aid", aid);
        request.set("pageState", pageState);
        MvcResult result = mockMvc.perform(post("/api/cards/list").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode fresh(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/cards").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void freshEntryReturnsFirstPageAndCommarea_frS0401() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(get("/api/cards").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("page"))
                .andExpect(jsonPath("$.screen.screenNumber").value(1))
                .andExpect(jsonPath("$.screen.rows.length()").value(7))
                .andExpect(jsonPath("$.screen.rows[0].cardNumber").value(key(1)))
                .andExpect(jsonPath("$.screen.rows[0].accountId").value(1))
                .andExpect(jsonPath("$.screen.rows[6].cardNumber").value(key(7)))
                .andExpect(jsonPath("$.pageState.firstCardNumber").value(key(1)))
                .andExpect(jsonPath("$.pageState.lastCardNumber").value(key(8)))
                .andExpect(jsonPath("$.pageState.nextPageExists").value(true));
    }

    @Test
    void pfEightThenPfSevenRoundTripsThroughCommarea_frS0403_frS0416() throws Exception {
        MockHttpSession session = signon();
        JsonNode first = fresh(session);

        JsonNode second = press(session, "PF8", first.get("pageState"));
        assertThat(second.at("/screen/screenNumber").asInt()).isEqualTo(2);
        assertThat(second.at("/screen/rows/0/cardNumber").asText()).isEqualTo(key(8));
        assertThat(second.at("/screen/rows/2/cardNumber").asText()).isEqualTo(key(10));
        assertThat(second.at("/screen/rows/3/cardNumber").isNull()).isTrue();
        assertThat(second.at("/pageState/nextPageExists").asBoolean()).isFalse();
        assertThat(second.at("/screen/errorMessage").asText())
                .isEqualTo("NO MORE RECORDS TO SHOW");

        JsonNode back = press(session, "PF7", second.get("pageState"));
        assertThat(back.at("/screen/screenNumber").asInt()).isEqualTo(1);
        assertThat(back.at("/screen/rows/0/cardNumber").asText()).isEqualTo(key(1));
        assertThat(back.at("/pageState/lastCardNumber").asText()).isEqualTo(key(8));
        assertThat(back.at("/pageState/nextPageExists").asBoolean()).isTrue();
    }

    @Test
    void pfEightExhaustionShowsInfoThenNoMorePages_frS0404() throws Exception {
        MockHttpSession session = signon();
        // Landing on the last page via PF8 already arms last-page-shown and
        // shows the info line alongside the EOF error (:913-920).
        JsonNode lastPage = press(session, "PF8", fresh(session).get("pageState"));
        assertThat(lastPage.at("/screen/errorMessage").asText())
                .isEqualTo("NO MORE RECORDS TO SHOW");
        assertThat(lastPage.at("/screen/infoMessage").asText())
                .isEqualTo("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD");
        assertThat(lastPage.at("/pageState/lastPageShown").asBoolean()).isTrue();

        JsonNode once = press(session, "PF8", lastPage.get("pageState"));
        assertThat(once.at("/screen/errorMessage").asText())
                .isEqualTo("NO MORE PAGES TO DISPLAY");
    }

    @Test
    void enterRelistsFromCurrentFirstAnchor_frS0405() throws Exception {
        MockHttpSession session = signon();
        JsonNode second = press(session, "PF8", fresh(session).get("pageState"));
        JsonNode relist = press(session, "ENTER", second.get("pageState"));

        assertThat(relist.at("/screen/rows/0/cardNumber").asText()).isEqualTo(key(8));
        assertThat(relist.at("/screen/screenNumber").asInt()).isEqualTo(2);
    }

    @Test
    void unmappedAidBehavesAsEnter_frS0406() throws Exception {
        MockHttpSession session = signon();
        JsonNode first = fresh(session);
        JsonNode pa1 = press(session, "PA1", first.get("pageState"));
        JsonNode enter = press(session, "ENTER", first.get("pageState"));

        assertThat(pa1.at("/screen").toString()).isEqualTo(enter.at("/screen").toString());
    }

    @Test
    void invalidFilterRedisplaysEchoedRowsWithError_frS0407() throws Exception {
        MockHttpSession session = signon();
        JsonNode first = fresh(session);
        ObjectNode request = objectMapper.createObjectNode();
        request.put("aid", "ENTER");
        request.put("accountFilter", "ABC");
        request.set("pageState", first.get("pageState"));
        mockMvc.perform(post("/api/cards/list").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen.errorMessage").value(
                        "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"))
                .andExpect(jsonPath("$.screen.accountFilter").value("ABC"))
                .andExpect(jsonPath("$.screen.accountFilterInvalid").value(true))
                .andExpect(jsonPath("$.screen.rows[0].cardNumber").value(key(1)))
                .andExpect(jsonPath("$.screen.infoMessage").value(nullValue()));
    }

    @Test
    void accountFilterLimitsRowsToMatchingAccount_frS0408() throws Exception {
        MockHttpSession session = signon();
        JsonNode first = fresh(session);
        ObjectNode request = objectMapper.createObjectNode();
        request.put("aid", "ENTER");
        request.put("accountFilter", "00000000002");
        request.set("pageState", first.get("pageState"));
        mockMvc.perform(post("/api/cards/list").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen.rows[0].cardNumber").value(key(8)))
                .andExpect(jsonPath("$.screen.rows[2].cardNumber").value(key(10)))
                .andExpect(jsonPath("$.screen.rows[3].cardNumber").value(nullValue()));
    }

    @Test
    void blankFiltersLeaveSelectsUnprotected_frS0410() throws Exception {
        MockHttpSession session = signon();
        JsonNode first = fresh(session);
        ObjectNode request = objectMapper.createObjectNode();
        request.put("aid", "ENTER");
        request.put("accountFilter", "");
        request.put("cardFilter", "");
        request.set("pageState", first.get("pageState"));
        mockMvc.perform(post("/api/cards/list").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen.rows[0].cardNumber").value(key(1)))
                .andExpect(jsonPath("$.screen.rows[0].selectProtected").value(false));
    }

    @Test
    void enterSelectSNavigatesWithRowKeys_frS0414() throws Exception {
        MockHttpSession session = signon();
        JsonNode first = fresh(session);
        ObjectNode request = objectMapper.createObjectNode();
        request.put("aid", "ENTER");
        request.set("pageState", first.get("pageState"));
        request.putArray("selections").add("").add("").add("S")
                .add("").add("").add("").add("");
        mockMvc.perform(post("/api/cards/list").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("navigate"))
                .andExpect(jsonPath("$.navigation.program").value("COCRDSLC"))
                .andExpect(jsonPath("$.navigation.action").value("S"))
                .andExpect(jsonPath("$.navigation.accountId").value(1))
                .andExpect(jsonPath("$.navigation.cardNumber").value(key(3)));
    }

    @Test
    void enterSelectUNavigatesWithRowKeys_frS0415() throws Exception {
        MockHttpSession session = signon();
        JsonNode first = fresh(session);
        ObjectNode request = objectMapper.createObjectNode();
        request.put("aid", "ENTER");
        request.set("pageState", first.get("pageState"));
        request.putArray("selections").add("").add("").add("").add("")
                .add("").add("").add("U");
        mockMvc.perform(post("/api/cards/list").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("navigate"))
                .andExpect(jsonPath("$.navigation.program").value("COCRDUPC"))
                .andExpect(jsonPath("$.navigation.cardNumber").value(key(7)));
    }

    @Test
    void pfThreeExits_frS0422() throws Exception {
        MockHttpSession session = signon();
        JsonNode result = press(session, "PF3", fresh(session).get("pageState"));
        assertThat(result.at("/outcome").asText()).isEqualTo("exit");
    }

    @Test
    void backwardExhaustionShowsVerbatimFileError_s04B4() throws Exception {
        // A selection error on page 2 re-browses from file start keeping the
        // page number (S04-B2); PF7 from there runs into BOF, lands on page 1
        // and shows the no-previous-pages override instead of the file error.
        MockHttpSession session = signon();
        JsonNode second = press(session, "PF8", fresh(session).get("pageState"));
        ObjectNode selectError = objectMapper.createObjectNode();
        selectError.put("aid", "ENTER");
        selectError.set("pageState", second.get("pageState"));
        selectError.putArray("selections").add("").add("").add("")
                .add("").add("").add("").add("X");
        JsonNode rebrowsed = objectMapper.readTree(mockMvc.perform(
                        post("/api/cards/list").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(selectError)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(rebrowsed.at("/pageState/firstCardNumber").asText()).isEqualTo(key(1));
        assertThat(rebrowsed.at("/pageState/screenNumber").asInt()).isEqualTo(2);

        JsonNode back = press(session, "PF7", rebrowsed.get("pageState"));
        assertThat(back.at("/screen/errorMessage").asText())
                .isEqualTo("NO PREVIOUS PAGES TO DISPLAY");
        assertThat(back.at("/pageState/firstCardNumber").asText()).isEqualTo(key(1));
        assertThat(back.at("/screen/rows/6/cardNumber").isNull()).isTrue();

        // Landing above page 1 keeps the file error: page 3, first anchor c3
        // with only two records before it — BOF mid-page (COCRDLIC.cbl:1361).
        ObjectNode crafted = second.get("pageState").deepCopy();
        crafted.put("screenNumber", 3);
        crafted.put("firstCardNumber", key(3));
        JsonNode fileError = press(session, "PF7", crafted);
        assertThat(fileError.at("/screen/errorMessage").asText()).isEqualTo(
                "File Error: READ     on CARDDAT   returned RESP 000000020 ,RESP2 000000090 ");
        assertThat(fileError.at("/pageState/screenNumber").asInt()).isEqualTo(2);
        assertThat(fileError.at("/pageState/firstCardNumber").asText()).isEqualTo(key(3));
        assertThat(fileError.at("/screen/rows/5/cardNumber").asText()).isEqualTo(key(1));
        assertThat(fileError.at("/screen/rows/6/cardNumber").asText()).isEqualTo(key(2));
    }

    @Test
    void verbatimErrorMessagesMatchTheCatalogue_frS0423() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(get("/api/cards").param("cardNumber", "123").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen.errorMessage").value(
                        "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"));
        mockMvc.perform(get("/api/cards").param("accountId", "00000000009").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen.errorMessage").value(
                        "NO RECORDS FOUND FOR THIS SEARCH CONDITION."));
    }
}
