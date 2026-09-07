package com.carddemo.api;

import java.math.BigDecimal;

public record BillPaymentResponse(Long accountId, BigDecimal paymentAmount,
                                  BigDecimal remainingBalance, String transactionId) {
}
