package com.carddemo;

import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * CORPT00C web surface (S-10): the CORPT0A request map, its AID-key
 * handling and the menu route must behave like the 3270 transaction.
 * A confirmed submit launches the real cbtrn03Job into an isolated
 * output dir, so the file contents double as the end-to-end check.
 * Test names carry the FR-S10 row each covers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:reportuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carddemo.batch.output-dir=target/test-s10-ui-batch"
})
class ReportUiIntegrationTest {

    private static final Path OUTPUT = Path.of("target/test-s10-ui-batch");

    @Autowired private MockMvc mockMvc;
    @Autowired private com.carddemo.repository.TransactionRepository transactions;

    @BeforeEach
    void cleanOutput() throws Exception {
        if (Files.exists(OUTPUT)) {
            try (var paths = Files.list(OUTPUT)) {
                for (Path path : paths.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        Files.createDirectories(OUTPUT);
    }

    @Test
    void reportScreenRendersTheCorpt0aInputMap() throws Exception {
        mockMvc.perform(get("/reports").session(signon()))
                .andExpect(status().isOk())
                .andExpect(view().name("reports"))
                .andExpect(content().string(containsString("CR00")))
                .andExpect(content().string(containsString("CORPT00C")))
                .andExpect(content().string(containsString("Transaction Reports")))
                .andExpect(content().string(containsString("name=\"monthly\"")))
                .andExpect(content().string(containsString("Monthly (Current Month)")))
                .andExpect(content().string(containsString("name=\"yearly\"")))
                .andExpect(content().string(containsString("Yearly (Current Year)")))
                .andExpect(content().string(containsString("name=\"custom\"")))
                .andExpect(content().string(containsString("Custom (Date Range)")))
                .andExpect(content().string(containsString("Start Date :")))
                .andExpect(content().string(containsString("  End Date :")))
                .andExpect(content().string(containsString("(MM/DD/YYYY)")))
                .andExpect(content().string(containsString("name=\"confirm\"")))
                .andExpect(content().string(containsString("(Y/N)")))
                .andExpect(content().string(containsString("ENTER=Continue  F3=Back")))
                .andExpect(model().attribute("message", nullValue()));
    }

    @Test
    void unsignedEntryBouncesToSignon_frS1020() throws Exception {
        mockMvc.perform(get("/reports"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        mockMvc.perform(post("/reports").param("aid", "ENTER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
    }

    @Test
    void menuOptionNineRoutesToTheReportScreen() throws Exception {
        mockMvc.perform(post("/menu/select").session(signon())
                        .param("aid", "ENTER").param("option", "9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reports"));
    }

    @Test
    void pf3ReturnsToTheMenu_frS1020() throws Exception {
        mockMvc.perform(post("/reports").session(signon())
                        .param("aid", "PF3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void invalidAidRedisplaysWithTheInvalidKeyMessage_frS1020() throws Exception {
        MvcResult result = mockMvc.perform(post("/reports").session(signon())
                        .param("aid", "F5").param("monthly", "X"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports"))
                .andExpect(content().string(containsString(
                        "Invalid key pressed. Please see below...")))
                .andReturn();
        com.carddemo.api.ReportScreen screen =
                (com.carddemo.api.ReportScreen) result.getModelAndView()
                        .getModel().get("screen");
        assertThat(screen.cursorField()).isEqualTo("monthly");
        assertThat(screen.monthly()).isEqualTo("X");
    }

    @Test
    void monthlySubmitLaunchesTheJobAndShowsGreenSuccess_frS1001_frS1014() throws Exception {
        MvcResult result = mockMvc.perform(post("/reports").session(signon())
                        .param("aid", "ENTER")
                        .param("monthly", "X").param("confirm", "Y"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports"))
                .andExpect(content().string(containsString(
                        "Monthly report submitted for printing ...")))
                .andReturn();
        com.carddemo.api.ReportScreen screen =
                (com.carddemo.api.ReportScreen) result.getModelAndView()
                        .getModel().get("screen");
        assertThat(screen.messageStyle()).isEqualTo("info");
        assertThat(screen.cursorField()).isEqualTo("monthly");
        assertThat(screen.monthly()).isEmpty();
        // The launch ran the real cbtrn03Job; September 2026 holds no rows
        // so the report is a faithful empty file.
        assertThat(OUTPUT.resolve("cbtrn03-report.txt")).exists();
    }

    @Test
    void customSubmitRunsTheJobAndWritesTheTypedRange_frS1003_frS1016()
            throws Exception {
        // The seed row's TRAN-PROC-TS field is spaces (the unloaded record
        // is 299 bytes) and its TRAN-CARD-NUM matches no xref — give it a
        // processed timestamp inside the range and the seeded card.
        var seed = transactions.findById("0000000000000001").orElseThrow();
        seed.setTranProcessTimestamp(java.time.LocalDateTime.parse("2022-06-10T19:27:53"));
        seed.setTranCardNumber("1111222233334444");
        transactions.save(seed);
        mockMvc.perform(post("/reports").session(signon())
                        .param("aid", "ENTER").param("custom", "X")
                        .param("sdtmm", "01").param("sdtdd", "01").param("sdtyyyy", "2022")
                        .param("edtmm", "12").param("edtdd", "31").param("edtyyyy", "2022")
                        .param("confirm", "Y"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Custom report submitted for printing ...")));
        String report = Files.readString(OUTPUT.resolve("cbtrn03-report.txt"));
        assertThat(report).startsWith("DALYREPT");
        assertThat(report).contains("Date Range: 2022-01-01 to 2022-12-31");
        assertThat(report).contains("0000000000000001");
    }

    @Test
    void blankStartMonthShowsTheVerbatimError_frS1005() throws Exception {
        MvcResult result = mockMvc.perform(post("/reports").session(signon())
                        .param("aid", "ENTER").param("custom", "X")
                        .param("sdtdd", "15").param("sdtyyyy", "2024")
                        .param("edtmm", "06").param("edtdd", "30").param("edtyyyy", "2024"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports"))
                .andExpect(content().string(containsString(
                        "Start Date - Month can NOT be empty...")))
                .andReturn();
        com.carddemo.api.ReportScreen screen =
                (com.carddemo.api.ReportScreen) result.getModelAndView()
                        .getModel().get("screen");
        assertThat(screen.cursorField()).isEqualTo("sdtmm");
        assertThat(screen.custom()).isEqualTo("X");
        assertThat(screen.sdtdd()).isEqualTo("15");
    }

    @Test
    void blankConfirmPromptsForTheReport_frS1011() throws Exception {
        MvcResult result = mockMvc.perform(post("/reports").session(signon())
                        .param("aid", "ENTER").param("monthly", "X"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports"))
                .andExpect(content().string(containsString(
                        "Please confirm to print the Monthly report...")))
                .andReturn();
        com.carddemo.api.ReportScreen screen =
                (com.carddemo.api.ReportScreen) result.getModelAndView()
                        .getModel().get("screen");
        assertThat(screen.cursorField()).isEqualTo("confirm");
    }

    @Test
    void declinedConfirmClearsTheScreen_frS1012() throws Exception {
        mockMvc.perform(post("/reports").session(signon())
                        .param("aid", "ENTER")
                        .param("monthly", "X").param("confirm", "N"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports"))
                .andExpect(model().attribute("message", nullValue()));
        assertThat(OUTPUT.resolve("cbtrn03-report.txt")).doesNotExist();
    }

    @Test
    void invalidConfirmShowsTheVerbatimMessage_frS1013() throws Exception {
        MvcResult result = mockMvc.perform(post("/reports").session(signon())
                        .param("aid", "ENTER")
                        .param("monthly", "X").param("confirm", "Q"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports"))
                .andExpect(model().attribute("message",
                        "\"Q\" is not a valid value to confirm..."))
                .andReturn();
        com.carddemo.api.ReportScreen screen =
                (com.carddemo.api.ReportScreen) result.getModelAndView()
                        .getModel().get("screen");
        assertThat(screen.cursorField()).isEqualTo("confirm");
    }

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "ADMIN001").param("passwd", "PASSWORD"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
