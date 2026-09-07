package com.carddemo.service;

import com.carddemo.api.ScreenHeaderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * POPULATE-HEADER-INFO shared by the online screens (COSGN00C.cbl:175-204): COTTL01Y titles,
 * CSDAT01Y date/time (mm/dd/yy, hh:mm:ss) and the CICS ASSIGN APPLID/SYSID replaced by
 * configuration (B-0030).
 */
@Service
public class ScreenHeaderService {

    /** CCDA-TITLE01 / CCDA-TITLE02, app/cpy/COTTL01Y.cpy:18-22 (PIC X(40), trimmed). */
    public static final String TITLE01 = "AWS Mainframe Modernization";
    public static final String TITLE02 = "CardDemo";

    /** WS-CURDATE-MM-DD-YY / WS-CURTIME-HH-MM-SS, app/cpy/CSDAT01Y.cpy, COSGN00C.cbl:180-197. */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Clock clock;
    private final String applId;
    private final String sysId;

    public ScreenHeaderService(
            Clock clock,
            @Value("${carddemo.applid}") String applId,
            @Value("${carddemo.sysid}") String sysId) {
        this.clock = clock;
        this.applId = applId;
        this.sysId = sysId;
    }

    public ScreenHeaderResponse header(String tranId, String programName) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new ScreenHeaderResponse(tranId, programName, TITLE01, TITLE02,
                DATE.format(now), TIME.format(now), applId, sysId);
    }
}
