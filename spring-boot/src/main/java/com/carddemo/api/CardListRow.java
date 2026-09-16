package com.carddemo.api;

public record CardListRow(String selectionViewCode, String selectionUpdateCode,
                          Long accountId, String cardNumber, String activeStatus,
                          String viewEndpoint, String updateEndpoint) {
}
