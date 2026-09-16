package com.carddemo.api;

import com.carddemo.service.AccountUpdateForm;
import com.carddemo.service.AccountUpdateSnapshot;

/**
 * The round-trip body for the update flow: `updated` carries the raw screen
 * inputs (the ACUP-NEW-* values), `original` the fetched snapshot that stands
 * in for WS-THIS-PROGCOMMAREA (S03-B3).
 */
public record AccountUpdateRequest(
        AccountUpdateForm updated,
        AccountUpdateSnapshot original) {
}
