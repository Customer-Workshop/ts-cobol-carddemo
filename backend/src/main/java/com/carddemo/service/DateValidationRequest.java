package com.carddemo.service;

/**
 * CSUTLDTC linkage: LS-DATE X(10) and LS-DATE-FORMAT X(10) (app/cbl/CSUTLDTC.cbl:84-86).
 * Both are required; values longer than 10 are a caller error (the COBOL caller truncates).
 */
public record DateValidationRequest(String date, String mask) {
}
