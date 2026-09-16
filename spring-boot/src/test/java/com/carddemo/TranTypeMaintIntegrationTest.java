package com.carddemo;

import com.carddemo.model.SecurityUser;
import com.carddemo.model.TransactionCategory;
import com.carddemo.model.TransactionType;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionTypeRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COTRTUPC state machine over POST /api/tran-types/maint: one call per AID
 * press, each echoing the maint COMMAREA as state. Test names carry the
 * FR-S21 row they cover.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:trantypemaintit;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TranTypeMaintIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SecurityUserRepository userRepository;
    @Autowired private TransactionTypeRepository types;
    @Autowired private TransactionCategoryRepository categories;

    @BeforeEach
    void seedTypesAndUser() {
        SecurityUser admin = new SecurityUser();
        admin.setUserId("ADMIN001");
        admin.setFirstName("ADMIN");
        admin.setLastName("USER");
        admin.setPassword("PASSWORD");
        admin.setUserType("A");
        userRepository.save(admin);

        categories.deleteAll();
        types.deleteAll();
        saveType("01", "TYPE1 DESC");
        saveType("02", "TYPE2 DESC");

        TransactionCategory category = new TransactionCategory();
        TransactionCategory.Id id = new TransactionCategory.Id();
        id.setTranTypeCode("02");
        id.setTranCategoryCode(1);
        category.setId(id);
        category.setDescription("TYPE2 CAT");
        categories.save(category);
    }

    private void saveType(String code, String desc) {
        TransactionType type = new TransactionType();
        type.setTranType(code);
        type.setDescription(desc);
        types.save(type);
    }

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"ADMIN001\",\"password\":\"PASSWORD\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private JsonNode press(MockHttpSession session, String aid, String code,
                         String desc, JsonNode state) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("aid", aid);
        if (code != null) {
            request.put("trtypcd", code);
        }
        if (desc != null) {
            request.put("trtydsc", desc);
        }
        if (state != null) {
            request.set("state", state);
        }
        MvcResult result = mockMvc.perform(post("/api/tran-types/maint").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Fresh entry then ENTER with a search key → state S (hit) or X (miss). */
    private JsonNode search(MockHttpSession session, String code) throws Exception {
        JsonNode fresh = press(session, "ENTER", null, null, null);
        return press(session, "ENTER", code, null, fresh.get("state"));
    }

    @Test
    void blankBadAndZeroCodesFailTheSearchEdits_frS2108() throws Exception {
        MockHttpSession session = signon();
        JsonNode fresh = press(session, "ENTER", null, null, null);
        assertThat(fresh.at("/state/action").asText()).isEqualTo(" ");
        assertThat(fresh.at("/screen/infoMessage").asText())
                .isEqualTo("Enter transaction type to be maintained");

        JsonNode blank = press(session, "ENTER", "", "", fresh.get("state"));
        assertThat(blank.at("/screen/errorMessage").asText())
                .isEqualTo("Tran Type code must be supplied.");
        assertThat(blank.at("/state/action").asText()).isEqualTo(" ");

        JsonNode alpha = press(session, "ENTER", "AB", null, fresh.get("state"));
        assertThat(alpha.at("/screen/errorMessage").asText())
                .isEqualTo("Tran Type code must be numeric.");

        JsonNode zero = press(session, "ENTER", "00", null, fresh.get("state"));
        assertThat(zero.at("/screen/errorMessage").asText())
                .isEqualTo("Tran Type code must not be zero.");
    }

    @Test
    void searchHitShowsRecordAndMissPromptsCreate_frS2108() throws Exception {
        MockHttpSession session = signon();

        JsonNode hit = search(session, "1");
        assertThat(hit.at("/state/action").asText()).isEqualTo("S");
        assertThat(hit.at("/screen/trtypcd").asText()).isEqualTo("01");
        assertThat(hit.at("/screen/trtydsc").asText()).isEqualTo("TYPE1 DESC");
        assertThat(hit.at("/screen/descEditable").asBoolean()).isTrue();
        // NUMVAL zero-pads the posted '1' to '01'.

        JsonNode miss = search(session, "99");
        assertThat(miss.at("/state/action").asText()).isEqualTo("X");
        assertThat(miss.at("/screen/errorMessage").asText())
                .isEqualTo("No record found for this key in database");
        assertThat(miss.at("/screen/infoMessage").asText())
                .isEqualTo("Press F05 to add. F12 to cancel");
    }

    @Test
    void unchangedSubmitShortCircuitsWithNoChangeMessage_frS2108() throws Exception {
        MockHttpSession session = signon();
        JsonNode hit = search(session, "01");
        JsonNode same = press(session, "ENTER", "01", "TYPE1 DESC",
                hit.get("state"));
        assertThat(same.at("/screen/errorMessage").asText())
                .isEqualTo("No change detected with respect to values fetched.");
        assertThat(same.at("/state/action").asText()).isEqualTo("S");
    }

    @Test
    void f5SaveCommitsUpdateThroughNAndC_frS2109() throws Exception {
        MockHttpSession session = signon();
        JsonNode hit = search(session, "01");

        JsonNode review = press(session, "ENTER", "01", "RENAMED DESC",
                hit.get("state"));
        assertThat(review.at("/state/action").asText()).isEqualTo("N");
        assertThat(review.at("/screen/infoMessage").asText())
                .isEqualTo("Changes validated.Press F5 to save");

        JsonNode saved = press(session, "PF5", "01", "RENAMED DESC",
                review.get("state"));
        assertThat(saved.at("/state/action").asText()).isEqualTo("C");
        assertThat(saved.at("/screen/infoMessage").asText())
                .isEqualTo("Changes committed to database");
        assertThat(types.findById("01").orElseThrow().getDescription())
                .isEqualTo("RENAMED DESC");
    }

    @Test
    void f5FromNotFoundCreatesTheRecord_frS2109() throws Exception {
        MockHttpSession session = signon();
        JsonNode miss = search(session, "09");

        JsonNode create = press(session, "PF5", "09", null, miss.get("state"));
        assertThat(create.at("/state/action").asText()).isEqualTo("R");
        assertThat(create.at("/screen/infoMessage").asText())
                .isEqualTo("Enter new transaction type details.");
        assertThat(create.at("/screen/descEditable").asBoolean()).isTrue();

        JsonNode review = press(session, "ENTER", "09", "NEW TYPE DESC",
                create.get("state"));
        assertThat(review.at("/state/action").asText()).isEqualTo("N");

        JsonNode saved = press(session, "PF5", "09", "NEW TYPE DESC",
                review.get("state"));
        assertThat(saved.at("/state/action").asText()).isEqualTo("C");
        assertThat(types.findById("09").orElseThrow().getDescription())
                .isEqualTo("NEW TYPE DESC");
    }

    @Test
    void f4DeletesAfterConfirm_frS2110() throws Exception {
        MockHttpSession session = signon();
        JsonNode hit = search(session, "01");

        JsonNode confirm = press(session, "PF4", "01", null, hit.get("state"));
        assertThat(confirm.at("/state/action").asText()).isEqualTo("9");
        assertThat(confirm.at("/screen/infoMessage").asText())
                .isEqualTo("Delete this record ? Press F4 to confirm");

        JsonNode done = press(session, "PF4", "01", null, confirm.get("state"));
        assertThat(done.at("/state/action").asText()).isEqualTo("7");
        assertThat(done.at("/screen/infoMessage").asText())
                .isEqualTo("Delete successful.");
        assertThat(types.existsById("01")).isFalse();
    }

    @Test
    void f4DeleteWithChildrenStaysWithMinus532_frS2110() throws Exception {
        MockHttpSession session = signon();
        JsonNode hit = search(session, "02");
        JsonNode confirm = press(session, "PF4", "02", null, hit.get("state"));
        JsonNode denied = press(session, "PF4", "02", null, confirm.get("state"));
        assertThat(denied.at("/state/action").asText()).isEqualTo("8");
        assertThat(denied.at("/screen/errorMessage").asText())
                .startsWith("Please delete associated child records first:SQLCODE :")
                .contains("-532");
        assertThat(types.existsById("02")).isTrue();
    }

    @Test
    void f2EntryWithExistingCodeDisplaysTheRecord_ui07() throws Exception {
        MockHttpSession session = signon();
        // F2 from the list enters CREATE (R) directly: the XCTL arrives as
        // a fresh (not re-entered) state carrying fromProgram=COTRTLIC.
        ObjectNode state = objectMapper.createObjectNode();
        state.put("action", " ");
        state.put("reenter", false);
        state.put("fromProgram", "COTRTLIC");
        ObjectNode request = objectMapper.createObjectNode();
        request.put("aid", "ENTER");
        request.set("state", state);
        MvcResult entry = mockMvc.perform(post("/api/tran-types/maint")
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andReturn();
        JsonNode create = objectMapper.readTree(
                entry.getResponse().getContentAsString());
        assertThat(create.at("/state/action").asText()).isEqualTo("R");

        // An existing key switches to SHOW with the record populated —
        // not the edit-compare's "desc must be supplied" on the empty
        // incoming old fields.
        JsonNode hit = press(session, "ENTER", "01", null, create.get("state"));
        assertThat(hit.at("/state/action").asText()).isEqualTo("S");
        assertThat(hit.at("/screen/trtypcd").asText()).isEqualTo("01");
        assertThat(hit.at("/screen/trtydsc").asText()).isEqualTo("TYPE1 DESC");
        assertThat(hit.at("/screen/errorMessage").asText())
                .doesNotContain("Desc must be supplied");
    }

    @Test
    void f12CancelsUpdateAndDeleteWithVerbatimMessages_frS2111() throws Exception {
        MockHttpSession session = signon();
        JsonNode hit = search(session, "01");

        JsonNode review = press(session, "ENTER", "01", "RENAMED DESC",
                hit.get("state"));
        JsonNode cancelled = press(session, "PF12", "01", null,
                review.get("state"));
        assertThat(cancelled.at("/screen/errorMessage").asText())
                .isEqualTo("Update was cancelled");
        assertThat(cancelled.at("/state/action").asText()).isEqualTo("B");
        // Originals restore for the backed-out state.
        assertThat(cancelled.at("/screen/trtydsc").asText()).isEqualTo("TYPE1 DESC");

        JsonNode confirm = press(session, "PF4", "01", null, hit.get("state"));
        JsonNode bailed = press(session, "PF12", "01", null,
                confirm.get("state"));
        assertThat(bailed.at("/screen/errorMessage").asText())
                .isEqualTo("Delete was cancelled");
        assertThat(bailed.at("/state/action").asText()).isEqualTo(" ");
    }

    @Test
    void pfThreeReturnsToTheCallingProgram_frS2112() throws Exception {
        MockHttpSession session = signon();
        JsonNode hit = search(session, "01");

        JsonNode exit = press(session, "PF3", null, null, hit.get("state"));
        assertThat(exit.at("/outcome").asText()).isEqualTo("exit");
        assertThat(exit.at("/navigation/program").asText()).isEqualTo("COADM01C");

        // Entered from the list: PF3 returns there.
        JsonNode fresh = objectMapper.readTree(objectMapper
                .writeValueAsString(press(session, "ENTER", null, null, null)));
        ObjectNode state = fresh.get("state").deepCopy();
        state.put("fromProgram", "COTRTLIC");
        ObjectNode request = objectMapper.createObjectNode();
        request.put("aid", "PF3");
        request.set("state", state);
        MvcResult result = mockMvc.perform(post("/api/tran-types/maint")
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andReturn();
        JsonNode fromList = objectMapper.readTree(
                result.getResponse().getContentAsString());
        assertThat(fromList.at("/navigation/program").asText()).isEqualTo("COTRTLIC");
    }

    @Test
    void enterWhileConfirmDeleteIsInvalidKey_s21AidGate() throws Exception {
        MockHttpSession session = signon();
        JsonNode hit = search(session, "01");
        JsonNode confirm = press(session, "PF4", "01", null, hit.get("state"));
        JsonNode bad = press(session, "ENTER", "01", null, confirm.get("state"));
        assertThat(bad.at("/screen/errorMessage").asText())
                .isEqualTo("Invalid key pressed");
    }
}
