package com.carddemo.api;

public record CardUpdateRequest(
        String embossedName,
        String activeStatus,
        Integer expiryMonth,
        Integer expiryYear,
        CardSnapshot original) {

    public record CardSnapshot(
            String embossedName,
            String activeStatus,
            Integer expiryMonth,
            Integer expiryYear) {
    }
}
