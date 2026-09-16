package com.carddemo;

import com.carddemo.model.Card;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.SecurityUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COCRDUPC over REST: POST /api/cards/lookup + /api/cards/validate drive the
 * same six-state machine as the screen, and PUT /api/cards/{n} is the save.
 * Test names carry the FR-S06 row they cover. Runs against H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:cardupdateit;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CardUpdateIntegrationTest {

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
        MvcResult result = mockMvc.perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"USER0001\",\"password\":\"PASSWORD\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String turn(String aid, String acct, String card, String name,
                        String status, String month, String year, String day,
                        String commarea) {
        return """
                {"aid":"%s","accountId":%s,"cardNumber":%s,"embossedName":%s,
                 "activeStatus":%s,"expiryMonth":%s,"expiryYear":%s,"expiryDay":%s,
                 "commarea":%s}
                """.formatted(aid, quoted(acct), quoted(card), quoted(name),
                quoted(status), quoted(month), quoted(year), quoted(day), commarea);
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** The fetched-screen COMMAREA echo for the seeded card. */
    private static String commarea(String action) {
        return """
                {"changeAction":"%s","accountId":"00000000001",
                 "cardNumber":"1111222233334444","embossedName":"ADA BYRON",
                 "activeStatus":"Y","expiryYear":"2030","expiryMonth":"11",
                 "expiryDay":"30"}
                """.formatted(action);
    }

    private static String freshCommarea() {
        return "{\"changeAction\":\"\"}";
    }

    @Test
    void lookupFetchesByCardNumberOnlyAndShowsOldImage_frS0610() throws Exception {
        MockHttpSession session = signon();
        // cbl:1379-1380 — the account is never matched against the stored
        // card; a valid-but-different account still fetches the record.
        mockMvc.perform(post("/api/cards/lookup").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turn("ENTER", "00000000002", "1111222233334444",
                                null, null, null, null, null, freshCommarea())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeAction").value("S"))
                .andExpect(jsonPath("$.embossedName").value("ADA BYRON"))
                .andExpect(jsonPath("$.activeStatus").value("Y"))
                .andExpect(jsonPath("$.expiryMonth").value("11"))
                .andExpect(jsonPath("$.expiryYear").value("2030"))
                .andExpect(jsonPath("$.expiryDay").value("30"))
                .andExpect(jsonPath("$.infoMessage")
                        .value("Details of selected card shown above"))
                .andExpect(jsonPath("$.cursorField").value("crdname"));
    }

    @Test
    void lookupMissShowsNotFoundAndFlagsBothKeys_frS0611() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/api/cards/lookup").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turn("ENTER", "00000000001", "9999999999999999",
                                null, null, null, null, null, freshCommarea())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorMessage")
                        .value("Did not find cards for this search condition"))
                .andExpect(jsonPath("$.accountInvalid").value(true))
                .andExpect(jsonPath("$.cardInvalid").value(true))
                .andExpect(jsonPath("$.changeAction").value(""));
    }

    @Test
    void validateTurnRunsTheEditLadderAndPromotesToN_frS0620() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/api/cards/validate").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turn("ENTER", "00000000001", "1111222233334444",
                                "ADA LOVELACE", "Y", "11", "2030", "30",
                                commarea("S"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeAction").value("N"))
                .andExpect(jsonPath("$.infoMessage")
                        .value("Changes validated.Press F5 to save"));

        mockMvc.perform(post("/api/cards/validate").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turn("ENTER", "00000000001", "1111222233334444",
                                "Ada123", "X", "13", "1800", "30", commarea("S"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeAction").value("E"))
                .andExpect(jsonPath("$.errorMessage")
                        .value("Card name can only contain alphabets and spaces"));
    }

    @Test
    void pfFiveCommitsAndPreservesCvvAndAccount_frS0622() throws Exception {
        // D1 — the legacy program wrote CVV '000' and the typed account
        // back; the port keeps the stored values.
        MockHttpSession session = signon();
        mockMvc.perform(post("/api/cards/validate").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turn("PF5", "00000000009", "1111222233334444",
                                "ADA LOVELACE", "N", "12", "2031", "30",
                                commarea("N"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeAction").value("C"))
                .andExpect(jsonPath("$.infoMessage")
                        .value("Changes committed to database"));

        Card saved = cardRepository.findById("1111222233334444").orElseThrow();
        assertThat(saved.getCardEmbossedName()).isEqualTo("ADA LOVELACE");
        assertThat(saved.getCardActiveStatus()).isEqualTo("N");
        assertThat(saved.getCardExpirationDate()).isEqualTo(LocalDate.of(2031, 12, 30));
        assertThat(saved.getCardCvvCode()).isEqualTo(123);
        assertThat(saved.getCardAcctId()).isEqualTo(1L);
    }

    @Test
    void pfFiveOnRecordChangedByOtherShowsReview_frS0623() throws Exception {
        MockHttpSession session = signon();
        Card stored = cardRepository.findById("1111222233334444").orElseThrow();
        stored.setCardEmbossedName("GRACE HOPPER");
        cardRepository.saveAndFlush(stored);

        mockMvc.perform(post("/api/cards/validate").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turn("PF5", "00000000001", "1111222233334444",
                                "ADA LOVELACE", "Y", "11", "2030", "30",
                                commarea("N"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeAction").value("S"))
                .andExpect(jsonPath("$.errorMessage")
                        .value("Record changed by some one else. Please review"))
                .andExpect(jsonPath("$.embossedName").value("GRACE HOPPER"));
    }

    @Test
    void pfFiveOnMissingRecordShowsLockError_frS0624() throws Exception {
        MockHttpSession session = signon();
        cardRepository.deleteAll();
        cardRepository.flush();

        mockMvc.perform(post("/api/cards/validate").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turn("PF5", "00000000001", "1111222233334444",
                                "ADA LOVELACE", "Y", "11", "2030", "30",
                                commarea("N"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeAction").value("L"))
                .andExpect(jsonPath("$.errorMessage")
                        .value("Could not lock record for update"));
    }

    @Test
    void nonCalendarExpiryOnPutFailsTheUpdate_frS0625d3() throws Exception {
        // D3 — PUT with expiryDay 30 in the snapshot and month 2 typed:
        // LocalDate.of(2030, 2, 30) has no such day and the save fails.
        MockHttpSession session = signon();
        mockMvc.perform(put("/api/cards/1111222233334444")
                        .param("accountId", "00000000001").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"embossedName":"ADA LOVELACE","activeStatus":"Y",
                                 "expiryMonth":2,"expiryYear":2030,
                                 "original":{"embossedName":"ADA BYRON",
                                             "activeStatus":"Y","expiryMonth":11,
                                             "expiryYear":2030,"expiryDay":30}}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Update of record failed"));
        assertThat(cardRepository.findById("1111222233334444").orElseThrow()
                .getCardExpirationDate()).isEqualTo(LocalDate.of(2030, 11, 30));
    }

    @Test
    void putOnDeletedCardShowsLockError_frS0624() throws Exception {
        MockHttpSession session = signon();
        cardRepository.deleteAll();
        cardRepository.flush();
        mockMvc.perform(put("/api/cards/1111222233334444")
                        .param("accountId", "00000000001").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"embossedName":"ADA LOVELACE","activeStatus":"Y",
                                 "expiryMonth":12,"expiryYear":2031,
                                 "original":{"embossedName":"ADA BYRON",
                                             "activeStatus":"Y","expiryMonth":11,
                                             "expiryYear":2030}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Could not lock record for update"));
    }
}
