package com.carddemo.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * B-0031: single place where legacy outcomes and framework failures become {@link ErrorResponse}.
 * Never leaks stack traces, SQL or Hibernate text (CORE drift rule 7).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CobolApiException.class)
    public ResponseEntity<ErrorResponse> handleCobol(CobolApiException ex) {
        return build(ex.getStatus(), ex.getMessage());
    }

    /** COMEN01C E-08 also carries the normalised OPTIONO echo (COMEN01C.cbl:125, FR §5 B-0010). */
    @ExceptionHandler(InvalidMenuOptionException.class)
    public ResponseEntity<MenuErrorResponse> handleInvalidMenuOption(InvalidMenuOptionException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new MenuErrorResponse(
                ex.getMessage(), ex.getStatus().value(), Instant.now(), ex.normalizedOption()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleProgrammingError(IllegalArgumentException ex) {
        log.error("Programming error reached the API boundary", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, CobolMessages.UNEXPECTED_ERROR);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleMissingResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, CobolMessages.UNEXPECTED_ERROR);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message, status.value(), Instant.now()));
    }
}
