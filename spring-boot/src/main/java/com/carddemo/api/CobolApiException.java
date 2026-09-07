package com.carddemo.api;

import org.springframework.http.HttpStatus;

public class CobolApiException extends RuntimeException {
    private final HttpStatus status;

    public CobolApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
