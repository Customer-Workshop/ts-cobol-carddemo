package com.carddemo.api;

/** FR-08: the SEND TEXT of the PF3 exit (COSGN00C.cbl:162-172) as a plain confirmation message. */
public record SignoffResponse(String message) {
}
