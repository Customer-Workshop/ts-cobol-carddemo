package com.carddemo.api;

public record CardUpdateRequest(
        String accountId,
        String cardNumber,
        String embossedName,
        String activeStatus,
        Integer expiryMonth,
        Integer expiryYear,
        String originalEmbossedName,
        String originalActiveStatus,
        Integer originalExpiryMonth,
        Integer originalExpiryYear) {
}
