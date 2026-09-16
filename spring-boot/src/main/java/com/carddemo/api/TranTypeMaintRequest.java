package com.carddemo.api;

/**
 * One AID press against CTTU: AID plus the posted map fields and the echoed
 * program COMMAREA.
 */
public record TranTypeMaintRequest(
        String aid,
        String trtypcd,
        String trtydsc,
        TranTypeMaintState state) {
}
