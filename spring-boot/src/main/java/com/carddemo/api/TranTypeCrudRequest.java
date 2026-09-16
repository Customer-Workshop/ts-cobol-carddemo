package com.carddemo.api;

/** REST CRUD body for POST/PUT on /api/tran-types. */
public record TranTypeCrudRequest(String tranType, String description) {
}
