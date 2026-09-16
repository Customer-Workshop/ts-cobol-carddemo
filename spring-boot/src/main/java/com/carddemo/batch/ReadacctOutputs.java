package com.carddemo.batch;

/** The four output records produced per account by readacctStep. */
public record ReadacctOutputs(String pscomp, String arryps, String vb1, String vb2) {
}
