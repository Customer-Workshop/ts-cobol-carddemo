package com.carddemo.batch;

/**
 * CBACT04C's CEE3ABD contract (app/cbl/CBACT04C.cbl:628-632, ABCODE 999):
 * raised wherever the legacy program would DISPLAY a diagnostic and abend —
 * a keyed ACCTFILE/XREFFIL1/DISCGRP read returning INVALID KEY (status 23),
 * or the DEFAULT disclosure fallback also missing. Unchecked so it propagates
 * through the chunk machinery to step FAILED; Abend999JobListener carries the
 * 999 exit code up to the job (S15-B7).
 */
public class InterestAbendException extends RuntimeException {
    public InterestAbendException(String message) {
        super(message);
    }
}
