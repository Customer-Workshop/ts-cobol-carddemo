package com.carddemo;

import com.carddemo.api.MenuSelectRequest;
import com.carddemo.model.SecurityUser;
import com.carddemo.model.TransactionCategory;
import com.carddemo.model.TransactionType;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionTypeRepository;
import com.carddemo.service.MenuService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COTRTLIC + COTRTUPC REST surface: GET /api/tran-types is the fresh entry and
 * POST /api/tran-types/list is one call per AID press, each echoing the paging
 * COMMAREA as pageState. Test names carry the FR-S21 row they cover.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:trantypelistit;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TranTypeListIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SecurityUserRepository userRepository;
    @Autowired private TransactionTypeRepository types;
    @Autowired private TransactionCategoryRepository categories;
    @Autowired private MenuService menuService;

    @BeforeEach
    void seedTypesAndUsers() {
        SecurityUser admin = new SecurityUser();
        admin.setUserId("ADMIN001");
        admin.setFirstName("ADMIN");
        admin.setLastName("USER");
        admin.setPassword("PASSWORD");
        admin.setUserType("A");
        userRepository.save(admin);

        SecurityUser regular = new SecurityUser();
        regular.setUserId("USER0001");
        regular.setFirstName("REGULAR");
        regular.setLastName("USER");
        regular.setPassword("PASSWORD");
        regular.setUserType("U");
        userRepository.save(regular);

        categories.deleteAll();
        types.deleteAll();
        for (int i = 1; i <= 10; i++) {
            TransactionType type = new TransactionType();
            type.setTranType("%02d".formatted(i));
            type.setDescription("TYPE%d DESC".formatted(i));
            types.save(type);
        }
        TransactionCategory category = new TransactionCategory();
        TransactionCategory.Id id = new TransactionCategory.Id();
        id.setTranTypeCode("02");
        id.setTranCategoryCode(1);
        category.setId(id);
        category.setDescription("TYPE2 CAT");
        categories.save(category);
    }

    private MockHttpSession signon(String userId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId
                                + "\",\"password\":\"PASSWORD\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private JsonNode fresh(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tran-types").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode press(MockHttpSession session, String aid, JsonNode pageState)
            throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("aid", aid);
        request.set("pageState", pageState);
        MvcResult result = mockMvc.perform(post("/api/tran-types/list").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode press(MockHttpSession session, ObjectNode request)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tran-types/list").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private ObjectNode requestWithSelects(String aid, JsonNode pageState,
                                          String... selects) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("aid", aid);
        request.set("pageState", pageState);
        ArrayNode array = request.putArray("selects");
        for (String select : selects) {
            array.add(select);
        }
        return request;
    }

    @Test
    void adminMenuOptionsRouteToBothScreens_frS2101() {
        assertThat(menuService.uiRouteForProgram("COTRTLIC")).isEqualTo("/ui/tran-types");
        assertThat(menuService.uiRouteForProgram("COTRTUPC")).isEqualTo("/ui/tran-types/maint");
        var list = menuService.selectAdmin(new MenuSelectRequest("5"));
        assertThat(list.implemented()).isTrue();
        assertThat(list.available()).isTrue();
        assertThat(list.message()).isNull();
        assertThat(menuService.uiRoute(list)).isEqualTo("/ui/tran-types");
        var maint = menuService.selectAdmin(new MenuSelectRequest("6"));
        assertThat(maint.implemented()).isTrue();
        assertThat(maint.available()).isTrue();
        assertThat(maint.message()).isNull();
        assertThat(menuService.uiRoute(maint)).isEqualTo("/ui/tran-types/maint");
    }

    @Test
    void freshEntryShowsFirstSevenRowsOrderedByCode_frS2102() throws Exception {
        MockHttpSession session = signon("ADMIN001");
        mockMvc.perform(get("/api/tran-types").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("page"))
                .andExpect(jsonPath("$.screen.screenNumber").value(1))
                .andExpect(jsonPath("$.screen.rows.length()").value(7))
                .andExpect(jsonPath("$.screen.rows[0].code").value("01"))
                .andExpect(jsonPath("$.screen.rows[6].code").value("07"))
                .andExpect(jsonPath("$.screen.rows[0].desc").value("TYPE1 DESC"))
                .andExpect(jsonPath("$.pageState.firstCode").value("01"))
                .andExpect(jsonPath("$.pageState.lastCode").value("08"))
                .andExpect(jsonPath("$.pageState.nextPageExists").value(true));
    }

    @Test
    void filtersRejectBadCodeAndReportNoMatch_frS2103() throws Exception {
        MockHttpSession session = signon("ADMIN001");
        JsonNode first = fresh(session);

        ObjectNode bad = objectMapper.createObjectNode();
        bad.put("aid", "ENTER");
        bad.put("trType", "AB");
        bad.set("pageState", first.get("pageState"));
        mockMvc.perform(post("/api/tran-types/list").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(bad)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen.errorMessage").value(
                        "TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER"))
                .andExpect(jsonPath("$.screen.typeFilterInvalid").value(true))
                .andExpect(jsonPath("$.screen.rows[0].code").value("01"));

        ObjectNode noMatch = objectMapper.createObjectNode();
        noMatch.put("aid", "ENTER");
        noMatch.put("trType", "99");
        noMatch.set("pageState", first.get("pageState"));
        mockMvc.perform(post("/api/tran-types/list").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(noMatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen.errorMessage").value(
                        "No Records found for these filter conditions"))
                .andExpect(jsonPath("$.screen.rows[0].code").value("01"));

        ObjectNode filter = objectMapper.createObjectNode();
        filter.put("aid", "ENTER");
        filter.put("trDesc", "TYPE9");
        filter.set("pageState", first.get("pageState"));
        mockMvc.perform(post("/api/tran-types/list").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(filter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen.rows[0].code").value("09"))
                .andExpect(jsonPath("$.screen.rows[1]").value(nullValue()));
    }

    @Test
    void pageEdgesShowVerbatimMessages_frS2104() throws Exception {
        MockHttpSession session = signon("ADMIN001");
        JsonNode first = fresh(session);

        JsonNode backAtTop = press(session, "PF7", first.get("pageState"));
        assertThat(backAtTop.at("/screen/errorMessage").asText())
                .isEqualTo("No previous pages to display");
        assertThat(backAtTop.at("/pageState/screenNumber").asInt()).isEqualTo(1);

        JsonNode second = press(session, "PF8", first.get("pageState"));
        assertThat(second.at("/screen/screenNumber").asInt()).isEqualTo(2);
        assertThat(second.at("/screen/rows/0/code").asText()).isEqualTo("08");
        assertThat(second.at("/screen/rows/2/code").asText()).isEqualTo("10");
        assertThat(second.at("/screen/rows/3").isNull()).isTrue();
        // First landing at EOF arms last-page-shown with the actions info.
        assertThat(second.at("/screen/errorMessage").asText())
                .isEqualTo("No more pages for these search conditions");
        assertThat(second.at("/screen/infoMessage").asText())
                .isEqualTo("Type U to update, D to delete any record");
        assertThat(second.at("/pageState/lastPageShown").asBoolean()).isTrue();

        JsonNode exhausted = press(session, "PF8", second.get("pageState"));
        assertThat(exhausted.at("/screen/errorMessage").asText())
                .isEqualTo("No more pages to display");

        JsonNode back = press(session, "PF7", second.get("pageState"));
        assertThat(back.at("/screen/rows/0/code").asText()).isEqualTo("01");
        assertThat(back.at("/pageState/screenNumber").asInt()).isEqualTo(1);
    }

    @Test
    void deleteFlagThenF10DeletesAfterConfirm_frS2105() throws Exception {
        MockHttpSession session = signon("ADMIN001");
        JsonNode first = fresh(session);

        JsonNode armed = press(session,
                requestWithSelects("ENTER", first.get("pageState"),
                        "D", "", "", "", "", "", ""));
        assertThat(armed.at("/screen/infoMessage").asText())
                .isEqualTo("Delete HIGHLIGHTED row ? Press F10 to confirm");
        assertThat(armed.at("/pageState/deletePending").asBoolean()).isTrue();

        JsonNode done = press(session,
                requestWithSelects("PF10", armed.get("pageState"),
                        "D", "", "", "", "", "", ""));
        assertThat(done.at("/screen/infoMessage").asText())
                .isEqualTo("HIGHLIGHTED row deleted.Hit Enter to continue");
        assertThat(types.existsById("01")).isFalse();
        // Successful delete wipes the COMMAREA — next ENTER is a fresh page.
        assertThat(done.at("/pageState/screenNumber").asInt()).isEqualTo(0);
    }

    @Test
    void updateFlagThenF10WritesNewDescription_frS2105() throws Exception {
        MockHttpSession session = signon("ADMIN001");
        JsonNode first = fresh(session);

        ObjectNode enter = requestWithSelects("ENTER", first.get("pageState"),
                "", "U", "", "", "", "", "");
        enter.putArray("rowDescs").add("").add("TYPE2 EDITED")
                .add("").add("").add("").add("").add("");
        JsonNode armed = press(session, enter);
        assertThat(armed.at("/screen/infoMessage").asText())
                .isEqualTo("Update HIGHLIGHTED row. Press F10 to save");
        assertThat(armed.at("/pageState/updatePending").asBoolean()).isTrue();

        ObjectNode f10 = requestWithSelects("PF10", armed.get("pageState"),
                "", "U", "", "", "", "", "");
        f10.putArray("rowDescs").add("").add("TYPE2 EDITED")
                .add("").add("").add("").add("").add("");
        JsonNode saved = press(session, f10);
        assertThat(saved.at("/screen/infoMessage").asText())
                .isEqualTo("HIGHLIGHTED row was updated");
        assertThat(types.findById("02").orElseThrow().getDescription())
                .isEqualTo("TYPE2 EDITED");
    }

    @Test
    void multipleOrBadFlagsRaiseTheVerbatimErrors_frS2106() throws Exception {
        MockHttpSession session = signon("ADMIN001");
        JsonNode first = fresh(session);

        ObjectNode two = requestWithSelects("ENTER", first.get("pageState"),
                "D", "U", "", "", "", "", "");
        two.putArray("rowDescs").add("TYPE1 DESC").add("TYPE2 DESC")
                .add("").add("").add("").add("").add("");
        JsonNode twoRows = press(session, two);
        assertThat(twoRows.at("/screen/errorMessage").asText())
                .isEqualTo("Please select only 1 action");

        JsonNode invalid = press(session,
                requestWithSelects("ENTER", first.get("pageState"),
                        "X", "", "", "", "", "", ""));
        assertThat(invalid.at("/screen/errorMessage").asText())
                .isEqualTo("Action code selected is invalid");
    }

    @Test
    void pfTwoNavigatesToMaintAndPfThreeExits_frS2107_frS2112() throws Exception {
        MockHttpSession session = signon("ADMIN001");
        JsonNode first = fresh(session);

        JsonNode add = press(session, "PF2", first.get("pageState"));
        assertThat(add.at("/outcome").asText()).isEqualTo("navigate");
        assertThat(add.at("/navigation/program").asText()).isEqualTo("COTRTUPC");
        assertThat(add.at("/navigation/fromProgram").asText()).isEqualTo("COTRTLIC");

        JsonNode exit = press(session, "PF3", first.get("pageState"));
        assertThat(exit.at("/outcome").asText()).isEqualTo("exit");
        assertThat(exit.at("/navigation/program").asText()).isEqualTo("COADM01C");
    }

    @Test
    void childGuardAndMissingRowsProduceVerbatimErrors_s21B4() throws Exception {
        MockHttpSession session = signon("ADMIN001");
        JsonNode first = fresh(session);

        // Type 02 has a child category: the list delete surfaces -532.
        JsonNode armed = press(session,
                requestWithSelects("ENTER", first.get("pageState"),
                        "", "D", "", "", "", "", ""));
        JsonNode denied = press(session,
                requestWithSelects("PF10", armed.get("pageState"),
                        "", "D", "", "", "", "", ""));
        assertThat(denied.at("/screen/errorMessage").asText())
                .startsWith("Please delete associated child records first:")
                .contains("-532");
        assertThat(types.existsById("02")).isTrue();
    }

    @Test
    void restCrudRoundTripWithVerbatimErrors_s21B3() throws Exception {
        MockHttpSession session = signon("ADMIN001");

        mockMvc.perform(get("/api/tran-types/3").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tranType").value("03"))
                .andExpect(jsonPath("$.description").value("TYPE3 DESC"));

        mockMvc.perform(post("/api/tran-types").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tranType\":\"11\",\"description\":\"NEW TYPE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tranType").value("11"));
        assertThat(types.existsById("11")).isTrue();

        mockMvc.perform(post("/api/tran-types").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tranType\":\"11\",\"description\":\"DUP\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString(
                        "Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:")))
                .andExpect(jsonPath("$.message", containsString("-803")));

        mockMvc.perform(put("/api/tran-types/11").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tranType\":\"11\",\"description\":\"RENAMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("RENAMED"));

        mockMvc.perform(delete("/api/tran-types/02").session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString(
                        "Please delete associated child records first:")));

        mockMvc.perform(delete("/api/tran-types/11").session(session))
                .andExpect(status().isOk());
        assertThat(types.existsById("11")).isFalse();

        mockMvc.perform(delete("/api/tran-types/99").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString(
                        "Delete failed with message:SQLCODE :")));
    }

    @Test
    void nonAdminIsRejectedWithAdminOnly_s21Gate() throws Exception {
        MockHttpSession session = signon("USER0001");
        mockMvc.perform(get("/api/tran-types").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "No access - Admin Only option... "));
    }
}
