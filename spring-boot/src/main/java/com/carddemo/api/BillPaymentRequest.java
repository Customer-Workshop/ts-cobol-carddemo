package com.carddemo.api;

public record BillPaymentRequest(String accountId, String confirmation) {
}
