package com.carddemo.service;

import com.carddemo.api.ScreenHeaderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** FR-01 / B-0030 — POPULATE-HEADER-INFO (COSGN00C.cbl:175-204) with a pinned clock. */
class ScreenHeaderServiceTest {

    @Test
    @DisplayName("FR-01 — header carries CC00, COSGN00C, COTTL01Y titles, mm/dd/yy, hh:mm:ss, APPLID, SYSID")
    void fr01Header() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T14:05:09Z"), ZoneOffset.UTC);
        ScreenHeaderService service = new ScreenHeaderService(clock, "CARDDEMO", "CICS");

        ScreenHeaderResponse header = service.header(AuthService.TRAN_ID, AuthService.PROGRAM_NAME);

        assertEquals("CC00", header.tranId());
        assertEquals("COSGN00C", header.programName());
        assertEquals("AWS Mainframe Modernization", header.title01());
        assertEquals("CardDemo", header.title02());
        assertEquals("09/07/26", header.currentDate());
        assertEquals("14:05:09", header.currentTime());
        assertEquals("CARDDEMO", header.applId());
        assertEquals("CICS", header.sysId());
    }
}
