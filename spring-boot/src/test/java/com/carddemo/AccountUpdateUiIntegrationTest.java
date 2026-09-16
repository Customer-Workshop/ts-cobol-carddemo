package com.carddemo;

import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.service.AccountUpdateForm;
import com.carddemo.service.AccountUpdateScreen;
import com.carddemo.service.AccountUpdateService;
import com.carddemo.service.AccountUpdateSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
 * COACTUPC web surface (tran CAUP, map CACTUPA): the six-state screen, its
 * AID keys, per-state protection and verbatim lines. FR-S03 rows named.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:accountupdateuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountUpdateUiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;
    @Autowired private AccountUpdateService service;

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

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "USER0001").param("passwd", "PASSWORD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private AccountUpdateScreen screen(MvcResult result) {
        return (AccountUpdateScreen) result.getModelAndView()
                .getModel().get("screen");
    }

    private MultiValueMap<String, String> fieldsOf(AccountUpdateScreen screen) {
        AccountUpdateForm f = screen.fields();
        MultiValueMap<String, String> p = new LinkedMultiValueMap<>();
        p.add("acctId", f.acctId());
        p.add("activeStatus", f.activeStatus());
        p.add("openYear", f.openYear());
        p.add("openMon", f.openMon());
        p.add("openDay", f.openDay());
        p.add("creditLimit", f.creditLimit());
        p.add("expYear", f.expYear());
        p.add("expMon", f.expMon());
        p.add("expDay", f.expDay());
        p.add("cashCreditLimit", f.cashCreditLimit());
        p.add("risYear", f.risYear());
        p.add("risMon", f.risMon());
        p.add("risDay", f.risDay());
        p.add("currentBalance", f.currentBalance());
        p.add("accountGroup", f.accountGroup());
        p.add("currentCycleCredit", f.currentCycleCredit());
        p.add("currentCycleDebit", f.currentCycleDebit());
        p.add("custId", f.custId());
        p.add("ssn1", f.ssn1());
        p.add("ssn2", f.ssn2());
        p.add("ssn3", f.ssn3());
        p.add("dobYear", f.dobYear());
        p.add("dobMon", f.dobMon());
        p.add("dobDay", f.dobDay());
        p.add("ficoScore", f.ficoScore());
        p.add("firstName", f.firstName());
        p.add("middleName", f.middleName());
        p.add("lastName", f.lastName());
        p.add("addrLine1", f.addrLine1());
        p.add("addrLine2", f.addrLine2());
        p.add("city", f.city());
        p.add("state", f.state());
        p.add("zip", f.zip());
        p.add("country", f.country());
        p.add("phone1a", f.phone1a());
        p.add("phone1b", f.phone1b());
        p.add("phone1c", f.phone1c());
        p.add("phone2a", f.phone2a());
        p.add("phone2b", f.phone2b());
        p.add("phone2c", f.phone2c());
        p.add("governmentId", f.governmentId());
        p.add("eftAccountId", f.eftAccountId());
        p.add("priCardHolder", f.priCardHolder());
        return p;
    }

    private MultiValueMap<String, String> origOf(AccountUpdateSnapshot o) {
        MultiValueMap<String, String> p = new LinkedMultiValueMap<>();
        p.add("orig.accountId", str(o.accountId()));
        p.add("orig.activeStatus", o.activeStatus());
        p.add("orig.currentBalance", str(o.currentBalance()));
        p.add("orig.creditLimit", str(o.creditLimit()));
        p.add("orig.cashCreditLimit", str(o.cashCreditLimit()));
        p.add("orig.openDate", str(o.openDate()));
        p.add("orig.expirationDate", str(o.expirationDate()));
        p.add("orig.reissueDate", str(o.reissueDate()));
        p.add("orig.currentCycleCredit", str(o.currentCycleCredit()));
        p.add("orig.currentCycleDebit", str(o.currentCycleDebit()));
        p.add("orig.accountGroup", o.accountGroup());
        p.add("orig.customerId", str(o.customerId()));
        p.add("orig.ssn", str(o.ssn()));
        p.add("orig.dateOfBirth", str(o.dateOfBirth()));
        p.add("orig.ficoScore", str(o.ficoScore()));
        p.add("orig.firstName", o.firstName());
        p.add("orig.middleName", o.middleName());
        p.add("orig.lastName", o.lastName());
        p.add("orig.addressLine1", o.addressLine1());
        p.add("orig.addressLine2", o.addressLine2());
        p.add("orig.addressLine3", o.addressLine3());
        p.add("orig.stateCode", o.stateCode());
        p.add("orig.zip", o.zip());
        p.add("orig.countryCode", o.countryCode());
        p.add("orig.phoneNumber1", o.phoneNumber1());
        p.add("orig.phoneNumber2", o.phoneNumber2());
        p.add("orig.governmentIssuedId", o.governmentIssuedId());
        p.add("orig.eftAccountId", o.eftAccountId());
        p.add("orig.primaryCardHolderIndicator", o.primaryCardHolderIndicator());
        return p;
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    /** The browser-equivalent: every hidden field the template emits. */
    private MultiValueMap<String, String> roundTrip(AccountUpdateScreen s) {
        MultiValueMap<String, String> p = fieldsOf(s);
        p.add("screenState", s.state().name());
        if (s.original() != null) {
            p.addAll(origOf(s.original()));
        }
        return p;
    }

    private AccountUpdateScreen enterSearch(MockHttpSession session,
                                            String acctId) throws Exception {
        MvcResult r = mockMvc.perform(post("/accounts/update").session(session)
                        .param("aid", "ENTER").param("acctId", acctId))
                .andExpect(status().isOk())
                .andExpect(view().name("account-update"))
                .andReturn();
        return screen(r);
    }

    private MvcResult postScreen(MockHttpSession session,
                                 AccountUpdateScreen s, String aid)
            throws Exception {
        return mockMvc.perform(post("/accounts/update").session(session)
                        .param("aid", aid).params(roundTrip(s)))
                .andExpect(status().isOk())
                .andExpect(view().name("account-update"))
                .andReturn();
    }

    /** Seed values that fail the ladder, repaired so edits can pass. */
    private MultiValueMap<String, String> repaired(AccountUpdateScreen s) {
        MultiValueMap<String, String> p = roundTrip(s);
        p.set("zip", "10100");
        p.set("eftAccountId", "1234567890");
        p.set("phone1a", "201");
        p.set("phone1b", "555");
        p.set("phone1c", "1212");
        // A stored "()- " renders as ")- " which fails the numeric edit —
        // an earlier save can leave that behind, so clear the parts.
        p.set("phone2a", "");
        p.set("phone2b", "");
        p.set("phone2c", "");
        p.set("dobYear", "1950");
        p.set("dobMon", "12");
        p.set("dobDay", "10");
        p.set("city", "Boston");
        // Tests share the H2 database, so always differ from the fetched
        // snapshot to clear the 1205 no-change gate.
        int fico = s.original().ficoScore() == null ? 800
                : s.original().ficoScore();
        p.set("ficoScore", fico == 801 ? "802" : "801");
        return p;
    }

    @Test
    void firstEntryShowsSearchPrompt_frS0301() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(get("/accounts/update").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("account-update"))
                .andExpect(content().string(containsString("COACTUPC")))
                .andExpect(content().string(containsString("Update Account")))
                .andExpect(content().string(containsString(
                        "Enter or update id of account to update")))
                .andExpect(content().string(containsString("F3=Exit")));
        assertThat(screen(mockMvc.perform(get("/accounts/update")
                .session(session)).andReturn()).state())
                .isEqualTo(AccountUpdateScreen.State.SEARCH);
    }

    @Test
    void blankOrStarSearchIsNoInputReceived_frS0302() throws Exception {
        MockHttpSession session = signon();
        for (String id : new String[] {"", "   ", "*"}) {
            AccountUpdateScreen s = enterSearch(session, id);
            assertThat(s.state()).isEqualTo(AccountUpdateScreen.State.SEARCH);
            assertThat(s.errorMessage()).isEqualTo("No input received");
            assertThat(s.accountFieldRed()).isTrue();
        }
    }

    @Test
    void validLookupRendersDetailsWithProtection_frS0307() throws Exception {
        MockHttpSession session = signon();
        MvcResult r = mockMvc.perform(post("/accounts/update").session(session)
                        .param("aid", "ENTER").param("acctId", "00000000001"))
                .andExpect(status().isOk())
                .andReturn();
        AccountUpdateScreen s = screen(r);
        assertThat(s.state()).isEqualTo(AccountUpdateScreen.State.DETAILS);
        assertThat(s.infoMessage())
                .isEqualTo("Update account details presented above.");
        String html = r.getResponse().getContentAsString();
        assertThat(html).contains("Customer Details");
        assertThat(html).containsPattern("id=\"acsttus\"[^>]*value=\"Y\"");
        // ACCTSID is ASKIP once fetched; ACSTNUM and ACSCTRY always are.
        assertThat(html).containsPattern(
                "id=\"acctsid\"[^>]*readonly");
        assertThat(html).containsPattern(
                "id=\"acstnum\"[^>]*readonly");
        assertThat(html).containsPattern(
                "id=\"acsctry\"[^>]*readonly");
        assertThat(html).doesNotContainPattern(
                "id=\"acsttus\"[^>]*readonly");
    }

    @Test
    void unchangedEnterReportsNoChangeDetected_frS0308() throws Exception {
        MockHttpSession session = signon();
        AccountUpdateScreen details = enterSearch(session, "00000000001");
        MvcResult r = postScreen(session, details, "ENTER");
        assertThat(screen(r).state())
                .isEqualTo(AccountUpdateScreen.State.DETAILS);
        assertThat(screen(r).errorMessage())
                .isEqualTo("No change detected with respect to values fetched.");
    }

    @Test
    void validEditsReachConfirmWithF5Lit_frS0323() throws Exception {
        MockHttpSession session = signon();
        AccountUpdateScreen details = enterSearch(session, "00000000001");
        MvcResult r = mockMvc.perform(post("/accounts/update").session(session)
                        .param("aid", "ENTER").params(repaired(details)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Changes validated.Press F5 to save")))
                .andReturn();
        AccountUpdateScreen confirm = screen(r);
        assertThat(confirm.state()).isEqualTo(AccountUpdateScreen.State.CONFIRM);
        assertThat(confirm.f5Lit()).isTrue();
        assertThat(confirm.f12Lit()).isTrue();
        // ENTER on the confirm screen redisplays it unchanged.
        MvcResult again = postScreen(session, confirm, "ENTER");
        assertThat(screen(again).state())
                .isEqualTo(AccountUpdateScreen.State.CONFIRM);
    }

    @Test
    void failedEditsRenderRedAndStarBlankedFields_frS0324() throws Exception {
        MockHttpSession session = signon();
        AccountUpdateScreen details = enterSearch(session, "00000000001");
        MultiValueMap<String, String> p = repaired(details);
        p.set("activeStatus", "X");
        p.set("firstName", "*");
        MvcResult r = mockMvc.perform(post("/accounts/update").session(session)
                        .param("aid", "ENTER").params(p))
                .andExpect(status().isOk())
                .andReturn();
        AccountUpdateScreen s = screen(r);
        assertThat(s.state()).isEqualTo(AccountUpdateScreen.State.EDIT_ERROR);
        assertThat(s.errorMessage())
                .isEqualTo("Account Status must be Y or N.");
        String html = r.getResponse().getContentAsString();
        // First-name flag BLANK renders `*`, status NOT_OK renders red.
        assertThat(html).containsPattern("id=\"acsfnam\"[^>]*value=\"\\*\"");
        assertThat(html).containsPattern(
                "id=\"acsttus\"[^>]*class=\"[^\"]*field-red");
        assertThat(s.f12Lit()).isTrue();
    }

    @Test
    void pf5InConfirmCommitsAndDoneEnterRefetches_frS0325_frS0330()
            throws Exception {
        MockHttpSession session = signon();
        AccountUpdateScreen details = enterSearch(session, "00000000001");
        MvcResult confirmResult = mockMvc.perform(
                        post("/accounts/update").session(session)
                                .param("aid", "ENTER").params(repaired(details)))
                .andReturn();
        AccountUpdateScreen confirm = screen(confirmResult);
        MvcResult saved = postScreen(session, confirm, "PF5");
        AccountUpdateScreen done = screen(saved);
        assertThat(done.state()).isEqualTo(AccountUpdateScreen.State.DONE);
        assertThat(done.infoMessage())
                .isEqualTo("Changes committed to database");
        // F5 and F12 are lit only in E/N/L/F — Done lights neither.
        assertThat(done.f5Lit()).isFalse();
        assertThat(done.f12Lit()).isFalse();
        // D3: ENTER in Done re-fetches into Details.
        MvcResult refetch = postScreen(session, done, "ENTER");
        assertThat(screen(refetch).state())
                .isEqualTo(AccountUpdateScreen.State.DETAILS);
        String expectedFico = "%03d".formatted(
                confirm.original().ficoScore() == 801 ? 802 : 801);
        assertThat(screen(refetch).fields().ficoScore())
                .isEqualTo(expectedFico);
    }

    @Test
    void f12RereadsAndClearsEdits_frS0331() throws Exception {
        MockHttpSession session = signon();
        AccountUpdateScreen details = enterSearch(session, "00000000001");
        MultiValueMap<String, String> p = roundTrip(details);
        p.set("firstName", "Changed");
        MvcResult edited = mockMvc.perform(post("/accounts/update")
                        .session(session).param("aid", "ENTER").params(p))
                .andReturn();
        assertThat(screen(edited).state())
                .isEqualTo(AccountUpdateScreen.State.EDIT_ERROR);
        MvcResult r = postScreen(session, screen(edited), "PF12");
        assertThat(screen(r).state())
                .isEqualTo(AccountUpdateScreen.State.DETAILS);
        assertThat(screen(r).errorMessage()).isNull();
        assertThat(screen(r).fields().firstName()).isEqualTo("Ada");
    }

    @Test
    void f3ExitsToMenuFromAnyState_frS0332() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/accounts/update").session(session)
                        .param("aid", "PF3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
        AccountUpdateScreen details = enterSearch(session, "00000000001");
        mockMvc.perform(post("/accounts/update").session(session)
                        .param("aid", "PF3").params(roundTrip(details)))
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void otherAidsShowInvalidKeyAndKeepTheScreen_frS0333() throws Exception {
        MockHttpSession session = signon();
        // F12 before anything is fetched is an invalid key too.
        MvcResult r = mockMvc.perform(post("/accounts/update").session(session)
                        .param("aid", "PF12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Invalid key pressed. Please see below...")))
                .andReturn();
        assertThat(screen(r).state()).isEqualTo(AccountUpdateScreen.State.SEARCH);

        AccountUpdateScreen details = enterSearch(session, "00000000001");
        r = postScreen(session, details, "F7");
        assertThat(screen(r).state())
                .isEqualTo(AccountUpdateScreen.State.DETAILS);
        assertThat(screen(r).errorMessage())
                .isEqualTo("Invalid key pressed. Please see below...");
        // F5 outside the confirm screen is an invalid key.
        r = postScreen(session, details, "PF5");
        assertThat(screen(r).state())
                .isEqualTo(AccountUpdateScreen.State.DETAILS);
        assertThat(screen(r).errorMessage())
                .isEqualTo("Invalid key pressed. Please see below...");
        assertThat(r.getResponse().getContentAsString())
                .containsPattern("id=\"f5key\"[^>]*class=\"dim\"");
    }
}
