package com.carddemo.api;

/** The B-0027 session identity (CDEMO-USER-ID / CDEMO-USER-TYPE, app/cpy/COCOM01Y.cpy:25-28). */
public record SessionResponse(String userId, String userType) {
}
