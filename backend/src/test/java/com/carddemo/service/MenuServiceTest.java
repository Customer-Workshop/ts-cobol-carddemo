package com.carddemo.service;

import com.carddemo.api.InvalidMenuOptionException;
import com.carddemo.api.MenuOption;
import com.carddemo.api.MenuResponse;
import com.carddemo.api.MenuSelectionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COMEN01C PROCESS-ENTER-KEY (app/cbl/COMEN01C.cbl:115-191) and the static option table
 * (app/cpy/COMEN02Y.cpy:19-90). Expectations derive from the COBOL and
 * functional/CardDemo/programs/COMEN01C_functional_requirement.md only.
 */
class MenuServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-09-07T14:05:09Z"), ZoneId.of("UTC"));

    private final MenuService menuService =
            new MenuService(new ScreenHeaderService(FIXED, "CARDDEMO", "CICS"));

    @Test
    @DisplayName("FR-09 / MEN-01 — header carries CM00 / COMEN01C and the Clock date-time (cbl:26-27, :240-257)")
    void fr09Header() {
        MenuResponse response = menuService.menu();
        assertEquals("CM00", response.header().tranId());
        assertEquals("COMEN01C", response.header().programName());
        assertEquals("AWS Mainframe Modernization", response.header().title01());
        assertEquals("CardDemo", response.header().title02());
        assertEquals("09/07/26", response.header().currentDate());
        assertEquals("14:05:09", response.header().currentTime());
    }

    @Test
    @DisplayName("B-0033 — the route table equals all 11 rows of COMEN02Y.cpy:19-90 (count 11, cpy:21)")
    void b0033RouteTableEqualsCopybook() {
        List<MenuOption> options = menuService.options();
        assertEquals(11, options.size());
        assertEquals(11, MenuService.OPTION_COUNT);

        assertEquals(List.of(
                        "01|Account View|COACTVWC|U|true",
                        "02|Account Update|COACTUPC|U|false",
                        "03|Credit Card List|COCRDLIC|U|false",
                        "04|Credit Card View|COCRDSLC|U|false",
                        "05|Credit Card Update|COCRDUPC|U|false",
                        "06|Transaction List|COTRN00C|U|false",
                        "07|Transaction View|COTRN01C|U|false",
                        "08|Transaction Add|COTRN02C|U|false",
                        "09|Transaction Reports|CORPT00C|U|false",
                        "10|Bill Payment|COBIL00C|U|false",
                        "11|Pending Authorization View|COPAUS0C|U|false"),
                options.stream()
                        .map(o -> String.format("%02d|%s|%s|%s|%s",
                                o.number(), o.name(), o.program(), o.userType(), o.implemented()))
                        .toList());
    }

    @Test
    @DisplayName("FR-09 / V-6 — BUILD-MENU-OPTIONS renders 'nn. name' per row (cbl:262-303)")
    void fr09OptionLabels() {
        assertEquals("01. Account View", menuService.options().get(0).label());
        assertEquals("11. Pending Authorization View", menuService.options().get(10).label());
    }

    @Test
    @DisplayName("B-0010 / B-0032 — only option 1 carries an endpoint and route; 2..11 carry neither")
    void b0010OnlyOptionOneIsImplemented() {
        MenuOption first = menuService.options().get(0);
        assertTrue(first.implemented());
        assertEquals("/api/accounts/{acctId}", first.endpoint());
        assertEquals("/accounts/view", first.route());

        menuService.options().stream().skip(1).forEach(option -> {
            assertFalse(option.implemented(), "option " + option.number());
            assertNull(option.endpoint(), "option " + option.number());
            assertNull(option.route(), "option " + option.number());
        });
    }

    @ParameterizedTest(name = "[{index}] ''{0}'' -> {1}")
    @CsvSource(nullValues = "NULL", value = {
            "'1 ', 01",
            "' 1', 01",
            "'01', 01",
            "'1',  01",
            "'  ', 00",
            "'',   00",
            "NULL, 00",
            "'0 ', 00",
            "'A ', 0A",
            "'1A', 1A",
            "'A1', A1",
            "'12', 12",
            "'11', 11",
            "'99', 99"
    })
    @DisplayName("FR-10 / V-1 — normalisation: right scan, JUST RIGHT move, blanks to zeros (cbl:117-125)")
    void fr10Normalisation(String raw, String expected) {
        assertEquals(expected, menuService.normalize(raw));
    }

    @Test
    @DisplayName("FR-10 / V-2 — blank option is 00 = ZEROS -> E-08 with the echo 00 (cbl:127-134)")
    void fr10BlankOption() {
        InvalidMenuOptionException ex =
                assertThrows(InvalidMenuOptionException.class, () -> menuService.select("  "));
        assertEquals("Please enter a valid option number...", ex.getMessage());
        assertEquals("00", ex.normalizedOption());
        assertEquals(400, ex.getStatus().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A ", "1A", "A1", "? ", "-1"})
    @DisplayName("FR-10 / V-2 — 'WS-OPTION IS NOT NUMERIC' -> E-08, echo is the normalised value (cbl:127-129)")
    void fr10NonNumericOption(String raw) {
        InvalidMenuOptionException ex =
                assertThrows(InvalidMenuOptionException.class, () -> menuService.select(raw));
        assertEquals("Please enter a valid option number...", ex.getMessage());
        assertEquals(menuService.normalize(raw), ex.normalizedOption());
    }

    @Test
    @DisplayName("FR-10 / V-2 — 'WS-OPTION = ZEROS' rejects an explicit 00 (cbl:129)")
    void fr10ZeroOption() {
        assertEquals("00", assertThrows(InvalidMenuOptionException.class,
                () -> menuService.select("00")).normalizedOption());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "13", "99"})
    @DisplayName("FR-10 / V-2+V-3 — '> CDEMO-MENU-OPT-COUNT' rejected before any table index (cbl:127-137)")
    void fr10OutOfRangeOption(String raw) {
        InvalidMenuOptionException ex =
                assertThrows(InvalidMenuOptionException.class, () -> menuService.select(raw));
        assertEquals("Please enter a valid option number...", ex.getMessage());
        assertEquals(raw, ex.normalizedOption());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "01", " 1", "1 "})
    @DisplayName("FR-11 / MEN-03 — option 1 dispatches to COACTVWC (cbl:177-187; COMEN02Y.cpy:25-29)")
    void fr11DispatchOptionOne(String raw) {
        MenuSelectionResponse response = menuService.select(raw);
        assertEquals("01", response.option());
        assertEquals("COACTVWC", response.program());
        assertEquals("/api/accounts/{acctId}", response.endpoint());
        assertEquals("/accounts/view", response.route());
        assertTrue(response.implemented());
        assertNull(response.message());
    }

    @ParameterizedTest
    @CsvSource({
            "02, COACTUPC", "03, COCRDLIC", "04, COCRDSLC", "05, COCRDUPC", "06, COTRN00C",
            "07, COTRN01C", "08, COTRN02C", "09, CORPT00C", "10, COBIL00C", "11, COPAUS0C"
    })
    @DisplayName("Q-12 / B-0032 — options 2..11 answer the facade, no route (plan §10 Q-12; cbl:145-188)")
    void q12UnavailableFacade(String raw, String program) {
        MenuSelectionResponse response = menuService.select(raw);
        assertEquals(raw, response.option());
        assertEquals(program, response.program());
        assertFalse(response.implemented());
        assertNull(response.endpoint());
        assertNull(response.route());
        assertEquals("Option not available in this release", response.message());
    }

    @Test
    @DisplayName("Q-01 / V-4 — selection does not depend on the session user type: the admin guard is dead (cbl:136-143)")
    void q01AdminSelectsLikeAnyUser() {
        MenuSelectionResponse response = menuService.select("1");
        assertTrue(response.implemented());
        assertEquals("/accounts/view", response.route());
        assertTrue(menuService.options().stream().allMatch(option -> "U".equals(option.userType())));
    }

    @Test
    @DisplayName("Q-14 — an option longer than OPTIONI PIC X(02) is rejected (COMEN01.bms:145)")
    void q14OverLengthOption() {
        assertThrows(IllegalArgumentException.class, () -> menuService.select("001"));
    }
}
