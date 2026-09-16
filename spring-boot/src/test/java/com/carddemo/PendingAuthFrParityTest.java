package com.carddemo;

import com.carddemo.api.CobolMessages;
import com.carddemo.api.PendingAuthKey;
import com.carddemo.api.PendingAuthPageState;
import com.carddemo.api.PendingAuthScreenView;
import com.carddemo.api.PendingAuthDetailScreen;
import com.carddemo.model.AuthFraud;
import com.carddemo.model.PendingAuthDetail;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.AuthFraudRepository;
import com.carddemo.repository.PendingAuthDetailRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * FR-S19-01..12 parity matrix (S19_functional_requirement.md / the COPAUS0C
 * /COPAUS1C/COPAUS2C program FRs). Fixtures come from PendingAuthDataSeeder
 * hung on the seeded acct 1 (cust 1): seven detail rows descending from
 * 02/29/24 to 02/14/24, the oldest pre-flagged 'F' + '02/20/24'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:pendingauthfr;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PendingAuthFrParityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;
    @Autowired private PendingAuthDetailRepository detailRepository;
    @Autowired private AuthFraudRepository fraudRepository;

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
    void menuOption11IsImplementedAndRoutesToScreen_frS1901() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "11"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/pending-auth"));
        mockMvc.perform(get("/ui/pending-auth").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("pending-auth"));
    }

    @Test
    void listShowsContextSummaryAndFirstFiveRows_frS1902() throws Exception {
        MockHttpSession session = signon();
        // REST list entry: context + PAUTSUM0 + the first page (COPAUS0C
        // MAINLINE first-entry gather).
        mockMvc.perform(get("/api/pending-auth/1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("page"))
                .andExpect(jsonPath("$.screen.acctIdInput").value("1"))
                .andExpect(jsonPath("$.screen.custName").value(containsString("Ada")))
                .andExpect(jsonPath("$.screen.apprCnt").value("005"))
                .andExpect(jsonPath("$.screen.declCnt").value("002"))
                .andExpect(jsonPath("$.screen.creditLimit").value("11000.00"))
                .andExpect(jsonPath("$.screen.rows[0].transactionId")
                        .value("T00000000000401"))
                .andExpect(jsonPath("$.screen.rows[4].transactionId").exists())
                .andExpect(jsonPath("$.pageState.pageNum").value(1))
                .andExpect(jsonPath("$.pageState.nextPage").value(true));

        MvcResult ui = mockMvc.perform(get("/ui/pending-auth")
                        .session(session).param("acct", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("pending-auth"))
                .andReturn();
        PendingAuthScreenView screen = (PendingAuthScreenView) ui.getModelAndView()
                .getModel().get("screen");
        assertThat(screen.custName()).startsWith("Ada");
        assertThat(screen.custId()).isEqualTo("000000001");
        assertThat(screen.rows()).hasSize(5);
        assertThat(screen.rows().stream().filter(r -> r != null)).hasSize(5);
    }

    @Test
    void rowsDisplayNewestFirstComplementOrder_frS1903() throws Exception {
        MockHttpSession session = signon();
        // Ascending (date9c,time9c) complement order renders newest real
        // time first (S19-B3, COPAUA0C.cbl:868-875).
        mockMvc.perform(get("/api/pending-auth/1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen.rows[0].date").value("02/29/24"))
                .andExpect(jsonPath("$.screen.rows[1].date").value("02/27/24"))
                .andExpect(jsonPath("$.screen.rows[2].date").value("02/24/24"))
                .andExpect(jsonPath("$.screen.rows[3].date").value("02/21/24"))
                .andExpect(jsonPath("$.screen.rows[4].date").value("02/19/24"));
    }

    @Test
    void pf8PagesForwardThroughDetails_frS1904() throws Exception {
        MockHttpSession session = signon();
        PendingAuthPageState state = firstPageState(session);
        // PF8: reposition strictly after PAUKEY-LAST, fill five + look-ahead.
        MvcResult forward = mockMvc.perform(withState(post("/ui/pending-auth").session(session)
                        .param("aid", "PF8"), state))
                .andExpect(status().isOk())
                .andExpect(view().name("pending-auth")).andReturn();
        assertThat(((PendingAuthPageState) forward.getModelAndView().getModel()
                .get("pageState")).pageNum()).isEqualTo(2);

        mockMvc.perform(get("/api/pending-auth/1/details")
                        .session(session).param("after", state.lastKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen.rows[0].date").value("02/17/24"))
                .andExpect(jsonPath("$.screen.rows[1].date").value("02/14/24"))
                .andExpect(jsonPath("$.screen.rows[2].transactionId").doesNotExist());
    }

    @Test
    void pf7PagesBackwardToStoredStartKey_frS1905() throws Exception {
        MockHttpSession session = signon();
        PendingAuthPageState state = firstPageState(session);
        // PF7 at page 1 stays put with the top-of-page message (:370-376).
        MvcResult top = mockMvc.perform(withState(post("/ui/pending-auth").session(session)
                        .param("aid", "PF7"), state))
                .andExpect(status().isOk())
                .andExpect(view().name("pending-auth")).andReturn();
        assertThat(((PendingAuthScreenView) top.getModelAndView().getModel()
                .get("screen")).message()).isEqualTo(CobolMessages.TRANSACTION_ALREADY_TOP);

        MvcResult page2 = mockMvc.perform(withState(post("/ui/pending-auth")
                        .session(session).param("aid", "PF8"), state))
                .andExpect(status().isOk()).andReturn();
        PendingAuthPageState state2 = (PendingAuthPageState) page2.getModelAndView()
                .getModel().get("pageState");
        assertThat(state2.pageNum()).isEqualTo(2);

        MvcResult back = mockMvc.perform(withState(post("/ui/pending-auth").session(session)
                        .param("aid", "PF7"), state2))
                .andExpect(status().isOk())
                .andExpect(view().name("pending-auth")).andReturn();
        PendingAuthPageState backState = (PendingAuthPageState) back.getModelAndView()
                .getModel().get("pageState");
        PendingAuthScreenView backScreen = (PendingAuthScreenView) back.getModelAndView()
                .getModel().get("screen");
        // COBOL quirk (COPAUS0C.cbl:436-443): the reposition fill re-runs the
        // WS-IDX=2 bump, so PAGE-NUM lands back on 2 and the stack slot is
        // clobbered with the displayed page's start key — PF7 only ever goes
        // back one page, then sticks. The visible rows are page 1's.
        assertThat(backState.pageNum()).isEqualTo(2);
        assertThat(backScreen.rows().get(0).date()).isEqualTo("02/29/24");
    }

    @Test
    void selectSNavigatesToDetail_frS1906() throws Exception {
        MockHttpSession session = signon();
        PendingAuthPageState state = firstPageState(session);
        String key = state.authKeys().get(0);
        mockMvc.perform(withState(post("/ui/pending-auth").session(session)
                        .param("aid", "ENTER")
                        .param("acctid", "1")
                        .param("sel", "S", "", "", "", ""), state))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/pending-auth/1/" + key));

        // A non-'S' select on a populated row redisplays the list error.
        MvcResult invalid = mockMvc.perform(withState(post("/ui/pending-auth").session(session)
                        .param("aid", "ENTER").param("acctid", "1")
                        .param("sel", "X", "", "", "", ""), state))
                .andExpect(status().isOk())
                .andExpect(view().name("pending-auth")).andReturn();
        assertThat(((PendingAuthScreenView) invalid.getModelAndView().getModel()
                .get("screen")).message())
                .isEqualTo(CobolMessages.TRANSACTION_SELECTION_INVALID);
    }

    @Test
    void detailShowsEveryCipaudtyField_frS1907() throws Exception {
        MockHttpSession session = signon();
        String key = keyOf(75939, 856487659);         // 240229 / 14:35:12.340
        mockMvc.perform(get("/api/pending-auth/1/details/" + key).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acctId").value(1))
                .andExpect(jsonPath("$.authKey").value(key))
                .andExpect(jsonPath("$.authDate").value("02/29/24"))
                .andExpect(jsonPath("$.authTime").value("14:35:12"))
                .andExpect(jsonPath("$.authResp").value("A"))
                .andExpect(jsonPath("$.authRespDeclined").value(false))
                .andExpect(jsonPath("$.authReason").value("0000-APPROVED"))
                .andExpect(jsonPath("$.authCode").value("000100"))
                .andExpect(jsonPath("$.amount").value("175.50"))
                .andExpect(jsonPath("$.posEntryMode").value("05"))
                .andExpect(jsonPath("$.messageSource").value("POS   "))
                .andExpect(jsonPath("$.mccCode").value("5411"))
                .andExpect(jsonPath("$.cardExpDate").value("12/26"))
                .andExpect(jsonPath("$.authType").value("0500"))
                .andExpect(jsonPath("$.transactionId").value("T00000000000401"))
                .andExpect(jsonPath("$.matchStatus").value("P"))
                .andExpect(jsonPath("$.fraudStatus").value("-"))
                .andExpect(jsonPath("$.merchantName").value("AMAZON MKTPLACE"))
                .andExpect(jsonPath("$.merchantId").value("M12345678901234"))
                .andExpect(jsonPath("$.merchantCity").value("SEATTLE"))
                .andExpect(jsonPath("$.merchantState").value("WA"))
                .andExpect(jsonPath("$.merchantZip").value("98101"))
                .andExpect(jsonPath("$.authTs").value("2024-02-29T14:35:12.34"));

        mockMvc.perform(get("/ui/pending-auth/1/" + key).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("pending-auth-detail"));

        // Declined row renders 'D' with its reason-table description.
        String declined = keyOf(75944, 835979499);    // 240224 / 16:40:20.500
        mockMvc.perform(get("/api/pending-auth/1/details/" + declined).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authResp").value("D"))
                .andExpect(jsonPath("$.authRespDeclined").value(true))
                .andExpect(jsonPath("$.authReason").value("4100-INSUFFICNT FUND"));
    }

    @Test
    void pf5MarksAuthorizationFraud_frS1908() throws Exception {
        MockHttpSession session = signon();
        String key = keyOf(75939, 856487659);
        // F5 = MARK-AUTH-FRAUD: toggle to 'F', stamp the report date,
        // journal AUTHFRDS (LINK COPAUS2C), REPL + SYNCPOINT (S19-B7).
        MvcResult result = mockMvc.perform(post("/ui/pending-auth/1/" + key)
                        .session(session).param("aid", "PF5"))
                .andExpect(status().isOk())
                .andExpect(view().name("pending-auth-detail"))
                .andReturn();
        PendingAuthDetailScreen screen = (PendingAuthDetailScreen) result
                .getModelAndView().getModel().get("screen");
        assertThat(screen.fraudStatus()).startsWith("F-");
        assertThat(screen.message()).isEqualTo(CobolMessages.PENDING_AUTH_FRAUD_MARKED);

        PendingAuthDetail stored = detailRepository
                .findById(new PendingAuthDetail.Id(1L, 75939, 856487659)).orElseThrow();
        assertThat(stored.getAuthFraud()).isEqualTo("F");
        assertThat(stored.getFraudRptDate())
                .isEqualTo(LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("MM/dd/yy")));

        String cardNum = stored.getCardNum().trim();
        AuthFraud journal = fraudRepository.findById(new AuthFraud.Id(
                cardNum, LocalDateTime.of(2024, 2, 29, 14, 35, 12, 340000000)))
                .orElseThrow();
        assertThat(journal.getAuthFraud()).isEqualTo("F");
        assertThat(journal.getAcctId()).isEqualTo(1L);
    }

    @Test
    void pf5OnFlaggedAuthRemovesFraud_frS1909() throws Exception {
        MockHttpSession session = signon();
        // The seeded oldest row (240214) is pre-flagged 'F' + '02/20/24';
        // F5 flips the 88-level intent to remove (COPAUS1C.cbl:238-244).
        String key = keyOf(75954, 877669249);
        MvcResult result = mockMvc.perform(post("/ui/pending-auth/1/" + key)
                        .session(session).param("aid", "PF5"))
                .andExpect(status().isOk()).andReturn();
        PendingAuthDetailScreen screen = (PendingAuthDetailScreen) result
                .getModelAndView().getModel().get("screen");
        assertThat(screen.fraudStatus()).startsWith("R-");
        assertThat(screen.message()).isEqualTo(CobolMessages.PENDING_AUTH_FRAUD_REMOVED);
        assertThat(detailRepository
                .findById(new PendingAuthDetail.Id(1L, 75954, 877669249))
                .orElseThrow().getAuthFraud()).isEqualTo("R");
    }

    @Test
    void pf8ShowsNextAuthorization_frS1910() throws Exception {
        MockHttpSession session = signon();
        String key = keyOf(75939, 856487659);
        // F8 = qualified re-read + unqualified GNP — the next auth hops.
        String nextKey = keyOf(75941, 908454876);     // 240227 / 09:15:45.123
        mockMvc.perform(post("/ui/pending-auth/1/" + key)
                        .session(session).param("aid", "PF8"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/pending-auth/1/" + nextKey));

        // On the last row the GNP end-of-data leaves the screen alone with
        // the already-at-last message (:289-294).
        String lastKey = keyOf(75954, 877669249);
        MvcResult last = mockMvc.perform(post("/ui/pending-auth/1/" + lastKey)
                        .session(session).param("aid", "PF8"))
                .andExpect(status().isOk())
                .andExpect(view().name("pending-auth-detail")).andReturn();
        PendingAuthDetailScreen screen = (PendingAuthDetailScreen) last
                .getModelAndView().getModel().get("screen");
        assertThat(screen.authKey()).isEqualTo(lastKey);
        assertThat(screen.message()).isEqualTo(CobolMessages.PENDING_AUTH_LAST_AUTH);
        assertThat(screen.hasNext()).isFalse();
    }

    @Test
    void pf3ReturnsToListAndMenu_frS1911() throws Exception {
        MockHttpSession session = signon();
        // Detail F3 XCTLs back to CPVS with the acct commarea.
        String key = keyOf(75939, 856487659);
        mockMvc.perform(post("/ui/pending-auth/1/" + key)
                        .session(session).param("aid", "PF3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/pending-auth?acct=1"));
        // List F3 is RETURN-TO-PREV-SCREEN → the menu (:660-678).
        PendingAuthPageState state = firstPageState(session);
        mockMvc.perform(withState(post("/ui/pending-auth").session(session)
                        .param("aid", "PF3"), state))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void fraudUpsertUpdatesExistingRowLikeDb2Minus803_frS1912() throws Exception {
        MockHttpSession session = signon();
        // -803 path (COPAUS2C.cbl:222-246): the (card_num, auth_ts) journal
        // row already exists, so the write lands as UPDATE, not INSERT.
        PendingAuthDetail stored = detailRepository
                .findById(new PendingAuthDetail.Id(1L, 75939, 856487659)).orElseThrow();
        AuthFraud.Id journalId = new AuthFraud.Id(stored.getCardNum().trim(),
                PendingAuthKey.realTimestamp(stored));
        AuthFraud preexisting = new AuthFraud();
        preexisting.setId(journalId);
        preexisting.setAuthFraud("R");
        preexisting.setFraudRptDate(LocalDate.of(2024, 1, 1));
        fraudRepository.save(preexisting);
        long before = fraudRepository.count();

        String key = keyOf(75939, 856487659);
        mockMvc.perform(post("/api/pending-auth/1/details/" + key + "/fraud")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fraudStatus").value(containsString("F-")))
                .andExpect(jsonPath("$.message")
                        .value(CobolMessages.PENDING_AUTH_FRAUD_MARKED));

        assertThat(fraudRepository.count()).isEqualTo(before);   // no dup insert
        AuthFraud journal = fraudRepository.findById(journalId).orElseThrow();
        assertThat(journal.getAuthFraud()).isEqualTo("F");
        assertThat(journal.getFraudRptDate()).isEqualTo(LocalDate.now());
    }

    // Tests share the seeded rows: restore the mutable fraud flags and the
    // journal so each test sees the same fixture.
    @BeforeEach
    void resetFraudFixture() {
        restore(75939, 856487659, " ", "        ");
        restore(75954, 877669249, "F", "02/20/24");
        detailRepository.findById(new PendingAuthDetail.Id(1L, 75939, 856487659))
                .ifPresent(detail -> fraudRepository.findById(new AuthFraud.Id(
                        detail.getCardNum().trim(), PendingAuthKey.realTimestamp(detail)))
                        .ifPresent(fraudRepository::delete));
    }

    private void restore(int date9c, int time9c, String flag, String rptDate) {
        detailRepository.findById(new PendingAuthDetail.Id(1L, date9c, time9c))
                .ifPresent(detail -> {
                    detail.setAuthFraud(flag);
                    detail.setFraudRptDate(rptDate);
                    detailRepository.save(detail);
                });
    }

    private PendingAuthPageState firstPageState(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/ui/pending-auth")
                        .session(session).param("acct", "1"))
                .andExpect(status().isOk()).andReturn();
        return (PendingAuthPageState) result.getModelAndView()
                .getModel().get("pageState");
    }

    private static String keyOf(int date9c, int time9c) {
        return new PendingAuthKey(date9c, time9c).encoded();
    }

    // Round-trips the CDEMO-CPVS-INFO COMMAREA the way the Thymeleaf hidden
    // inputs do (S19-B8).
    private static MockHttpServletRequestBuilder withState(
            MockHttpServletRequestBuilder builder, PendingAuthPageState state) {
        builder.param("stateAcct", state.acctId() == null ? "" : state.acctId().toString())
                .param("pageNum", String.valueOf(state.pageNum()))
                .param("lastKey", state.lastKey() == null ? "" : state.lastKey())
                .param("nextPage", String.valueOf(state.nextPage()));
        for (String prevKey : state.prevPageKeys()) {
            builder.param("prevKey", prevKey == null ? "" : prevKey);
        }
        for (String rowKey : state.authKeys()) {
            builder.param("rowKey", rowKey == null ? "" : rowKey);
        }
        return builder;
    }

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "USER0001").param("passwd", "PASSWORD"))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
