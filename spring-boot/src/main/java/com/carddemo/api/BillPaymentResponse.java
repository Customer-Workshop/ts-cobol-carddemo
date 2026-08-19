package com.carddemo.api;

import java.math.BigDecimal;

public record BillPaymentResponse(String accountId, BigDecimal paymentAmount,
                                  BigDecimal remainingBalance, String transactionId) {
}
