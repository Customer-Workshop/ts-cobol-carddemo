package com.carddemo.api;

/**
 * The customer half of map {@code CACTVWA} (COACTVWC.cbl:493-523), in screen order. {@code zipCode},
 * {@code phone1} and {@code phone2} carry the full stored values instead of the map's 5 / 13-character
 * windows (DV-05 / Q-11).
 */
public record CustomerBlock(
        String customerId,
        String ssn,
        String dateOfBirth,
        Integer ficoScore,
        String firstName,
        String middleName,
        String lastName,
        String addressLine1,
        String addressLine2,
        String city,
        String stateCode,
        String zipCode,
        String countryCode,
        String phone1,
        String phone2,
        String governmentIssuedId,
        String eftAccountId,
        String primaryCardHolder) {
}
