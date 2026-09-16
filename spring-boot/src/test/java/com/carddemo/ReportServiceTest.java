package com.carddemo;

import com.carddemo.api.ReportForm;
import com.carddemo.api.ReportScreen;
import com.carddemo.batch.BatchJobLauncherService;
import com.carddemo.service.DateValidationService;
import com.carddemo.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CORPT00C edit ladder at the service seam (S-10): report-type EVALUATE,
 * custom field edits via the CSUTLDTC port, the confirm gate and the
 * submit/launch mapping. Test names carry the FR-S10 row each covers;
 * cites live in `functional/CARDDEMO/programs/CORPT00C_functional_requirement.md`.
 */
class ReportServiceTest {

    private final BatchJobLauncherService launcher = mock(BatchJobLauncherService.class);
    private final ReportService service =
            new ReportService(launcher, new DateValidationService());

    @BeforeEach
    void stubLaunch() {
        when(launcher.launch(anyString(), any())).thenReturn(new JobExecution(1L));
    }

    @Test
    void monthlyDerivesCurrentMonthRange_frS1001() {
        ReportScreen screen = service.enter(form(
                "X", "", "", "", "", "", "", "", "", "Y"));

        assertThat(screen.message()).isEqualTo("Monthly report submitted for printing ...");
        assertThat(screen.messageStyle()).isEqualTo("info");
        YearMonth month = YearMonth.now();
        verify(launcher).launch(eq("cbtrn03Job"), eq(Map.of(
                "startDate", month.atDay(1).toString(),
                "endDate", month.atEndOfMonth().toString())));
    }

    @Test
    void yearlyDerivesFullYearRange_frS1002() {
        service.enter(form("", "X", "", "", "", "", "", "", "", "Y"));

        int year = LocalDate.now().getYear();
        verify(launcher).launch(eq("cbtrn03Job"), eq(Map.of(
                "startDate", LocalDate.of(year, 1, 1).toString(),
                "endDate", LocalDate.of(year, 12, 31).toString())));
    }

    @Test
    void customPassesTheTypedRangeThrough_frS1003() {
        service.enter(form("", "", "X", "06", "15", "2024",
                "06", "30", "2024", "Y"));

        verify(launcher).launch(eq("cbtrn03Job"), eq(Map.of(
                "startDate", "2024-06-15", "endDate", "2024-06-30")));
    }

    @Test
    void firstNonBlankTypeFlagWins_frS1004() {
        // Monthly + Custom both set: the EVALUATE order picks Monthly —
        // the custom dates (here deliberately garbage) are never edited.
        ReportScreen screen = service.enter(form(
                "X", "", "X", "AB", "CD", "EFGH", "", "", "", "Y"));

        assertThat(screen.message()).isEqualTo("Monthly report submitted for printing ...");
        YearMonth month = YearMonth.now();
        verify(launcher).launch(eq("cbtrn03Job"), eq(Map.of(
                "startDate", month.atDay(1).toString(),
                "endDate", month.atEndOfMonth().toString())));
    }

    @Test
    void blankCustomFieldsShowTheFieldMessageInSourceOrder_frS1005() {
        assertBlank(form("", "", "X", "", "15", "2024", "06", "30", "2024", ""),
                "Start Date - Month can NOT be empty...", "sdtmm");
        assertBlank(form("", "", "X", "06", "", "2024", "06", "30", "2024", ""),
                "Start Date - Day can NOT be empty...", "sdtdd");
        assertBlank(form("", "", "X", "06", "15", "", "06", "30", "2024", ""),
                "Start Date - Year can NOT be empty...", "sdtyyyy");
        assertBlank(form("", "", "X", "06", "15", "2024", "", "30", "2024", ""),
                "End Date - Month can NOT be empty...", "edtmm");
        assertBlank(form("", "", "X", "06", "15", "2024", "06", "", "2024", ""),
                "End Date - Day can NOT be empty...", "edtdd");
        assertBlank(form("", "", "X", "06", "15", "2024", "06", "30", "", ""),
                "End Date - Year can NOT be empty...", "edtyyyy");
    }

    private void assertBlank(ReportForm form, String message, String cursor) {
        ReportScreen screen = service.enter(form);
        assertThat(screen.message()).isEqualTo(message);
        assertThat(screen.cursorField()).isEqualTo(cursor);
    }

    @Test
    void nonNumericOrHighMonthShowsNotAValidMonth_frS1006() {
        assertInvalid(form("", "", "X", "13", "15", "2024", "06", "30", "2024", ""),
                "Start Date - Not a valid Month...", "sdtmm");
        assertInvalid(form("", "", "X", "AB", "15", "2024", "06", "30", "2024", ""),
                "Start Date - Not a valid Month...", "sdtmm");
        assertInvalid(form("", "", "X", "06", "15", "2024", "13", "30", "2024", ""),
                "End Date - Not a valid Month...", "edtmm");
    }

    @Test
    void nonNumericOrHighDayShowsNotAValidDay_frS1007() {
        assertInvalid(form("", "", "X", "06", "32", "2024", "06", "30", "2024", ""),
                "Start Date - Not a valid Day...", "sdtdd");
        assertInvalid(form("", "", "X", "06", "15", "2024", "06", "3A", "2024", ""),
                "End Date - Not a valid Day...", "edtdd");
    }

    @Test
    void nonNumericYearShowsNotAValidYear_frS1008() {
        assertInvalid(form("", "", "X", "06", "15", "ABCD", "06", "30", "2024", ""),
                "Start Date - Not a valid Year...", "sdtyyyy");
        assertInvalid(form("", "", "X", "06", "15", "2024", "06", "30", "WXYZ", ""),
                "End Date - Not a valid Year...", "edtyyyy");
    }

    private void assertInvalid(ReportForm form, String message, String cursor) {
        ReportScreen screen = service.enter(form);
        assertThat(screen.message()).isEqualTo(message);
        assertThat(screen.cursorField()).isEqualTo(cursor);
        verify(launcher, never()).launch(anyString(), any());
    }

    @Test
    void shortDigitsNormalizeBeforeTheRangeEdits_frS1009() {
        // NUMVAL-C: ' 7' -> '07' — passes the month edit, and the echoed
        // screen fields carry the normalized values.
        ReportScreen screen = service.enter(form(
                "", "", "X", " 7", "15", "2024", " 8", " 1", "2024", ""));

        assertThat(screen.message()).isEqualTo(
                "Please confirm to print the Custom report...");
        assertThat(screen.sdtmm()).isEqualTo("07");
        assertThat(screen.edtmm()).isEqualTo("08");
        assertThat(screen.edtdd()).isEqualTo("01");
    }

    @Test
    void datesRunThroughCsutldtcWithThe2513Tolerance_frS1010() {
        // 02/30 is a real calendar failure — sev 0003, msg 2508 -> rejected.
        ReportScreen bad = service.enter(form(
                "", "", "X", "02", "30", "2024", "06", "30", "2024", ""));
        assertThat(bad.message()).isEqualTo("Start Date - Not a valid date...");
        assertThat(bad.cursorField()).isEqualTo("sdtmm");

        ReportScreen badEnd = service.enter(form(
                "", "", "X", "02", "15", "2024", "02", "30", "2024", ""));
        assertThat(badEnd.message()).isEqualTo("End Date - Not a valid date...");
        assertThat(badEnd.cursorField()).isEqualTo("edtmm");

        // Year 0000 returns msg 2513 — tolerated per the caller contract.
        ReportScreen tolerated = service.enter(form(
                "", "", "X", "06", "15", "0000", "06", "30", "2024", ""));
        assertThat(tolerated.message()).isEqualTo(
                "Please confirm to print the Custom report...");
    }

    @Test
    void blankConfirmPromptsForTheReport_frS1011() {
        ReportScreen screen = service.enter(form(
                "X", "", "", "", "", "", "", "", "", ""));

        assertThat(screen.message()).isEqualTo(
                "Please confirm to print the Monthly report...");
        assertThat(screen.cursorField()).isEqualTo("confirm");
        assertThat(screen.monthly()).isEqualTo("X");
        verify(launcher, never()).launch(anyString(), any());
    }

    @Test
    void declinedConfirmClearsTheScreenWithoutSubmitting_frS1012() {
        ReportScreen screen = service.enter(form(
                "X", "", "", "", "", "", "", "", "", "n"));

        assertThat(screen).isEqualTo(ReportScreen.blank());
        verify(launcher, never()).launch(anyString(), any());
    }

    @Test
    void invalidConfirmShowsTheVerbatimMessage_frS1013() {
        ReportScreen screen = service.enter(form(
                "X", "", "", "", "", "", "", "", "", "Q"));

        assertThat(screen.message()).isEqualTo(
                "\"Q\" is not a valid value to confirm...");
        assertThat(screen.cursorField()).isEqualTo("confirm");
        verify(launcher, never()).launch(anyString(), any());
    }

    @Test
    void confirmedSubmitLaunchesAndShowsGreenSuccess_frS1014() {
        ReportScreen screen = service.enter(form(
                "", "", "X", "06", "15", "2024", "06", "30", "2024", "Y"));

        assertThat(screen.message()).isEqualTo(
                "Custom report submitted for printing ...");
        assertThat(screen.messageStyle()).isEqualTo("info");
        assertThat(screen.cursorField()).isEqualTo("monthly");
        assertThat(screen.custom()).isEmpty();
        assertThat(screen.sdtyyyy()).isEmpty();
        verify(launcher).launch(eq("cbtrn03Job"), eq(Map.of(
                "startDate", "2024-06-15", "endDate", "2024-06-30")));
    }

    @Test
    void launchFailureShowsTheTdqWriteError_frS1015() {
        when(launcher.launch(anyString(), any()))
                .thenThrow(new IllegalStateException("Unable to launch batch job cbtrn03Job"));

        ReportScreen screen = service.enter(form(
                "X", "", "", "", "", "", "", "", "", "Y"));

        assertThat(screen.message()).isEqualTo("Unable to Write TDQ (JOBS)...");
        assertThat(screen.cursorField()).isEqualTo("monthly");
        assertThat(screen.messageStyle()).isNull();
    }

    @Test
    void enterWithNoTypeShowsTheSelectPrompt() {
        ReportScreen screen = service.enter(form(
                "", "", "", "06", "15", "2024", "06", "30", "2024", ""));

        assertThat(screen.message()).isEqualTo("Select a report type to print report...");
        assertThat(screen.cursorField()).isEqualTo("monthly");
        verify(launcher, never()).launch(anyString(), any());
    }

    @Test
    void invertedCustomRangeIsRejected_deviationD1() {
        ReportScreen screen = service.enter(form(
                "", "", "X", "06", "30", "2024", "06", "15", "2024", ""));

        assertThat(screen.message()).isEqualTo(
                "End Date must not be before Start Date...");
        verify(launcher, never()).launch(anyString(), any());
    }

    private static ReportForm form(String monthly, String yearly, String custom,
                                   String sdtmm, String sdtdd, String sdtyyyy,
                                   String edtmm, String edtdd, String edtyyyy,
                                   String confirm) {
        return new ReportForm(monthly, yearly, custom, sdtmm, sdtdd, sdtyyyy,
                edtmm, edtdd, edtyyyy, confirm);
    }
}
