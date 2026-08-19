package com.carddemo.api;

import java.time.LocalDate;

public record CardResponse(String cardNumber, Long accountId, Integer cvvCode,
                           String embossedName, LocalDate expirationDate,
                           String activeStatus) {
}
