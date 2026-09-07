package com.carddemo.api;

import java.time.Instant;

public record ErrorResponse(String message, int status, Instant timestamp) {
}
