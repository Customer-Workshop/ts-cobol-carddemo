package com.carddemo.api;

/**
 * POPULATE-HEADER-INFO (COSGN00C.cbl:175-204) for map COSGN0A: transaction id, program name,
 * the two COTTL01Y title lines, date mm/dd/yy, time hh:mm:ss and the CICS APPLID / SYSID
 * (B-0030: configuration properties plus the server clock).
 */
public record ScreenHeaderResponse(
        String tranId,
        String programName,
        String title01,
        String title02,
        String currentDate,
        String currentTime,
        String applId,
        String sysId) {
}
