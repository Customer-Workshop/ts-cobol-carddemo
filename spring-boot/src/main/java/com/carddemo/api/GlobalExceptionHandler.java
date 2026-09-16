package com.carddemo.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // A program's own gate (COTRTLIC/menu access) hit from the UI renders the
    // in-app denied page at 403; the JSON contract stays for the API surface.
    @ExceptionHandler(CobolApiException.class)
    void handleCobol(CobolApiException exception, HttpServletRequest request,
                     HttpServletResponse response) throws Exception {
        if (exception.getStatus() == HttpStatus.FORBIDDEN && !isApiSurface(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            request.getRequestDispatcher("/ui/denied").forward(request, response);
            return;
        }
        response.setStatus(exception.getStatus().value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponse(exception.getMessage(), exception.getStatus().value(),
                        Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        FieldError error = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        return responseEntity(HttpStatus.BAD_REQUEST, error == null ? "Invalid request" : error.getDefaultMessage());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ErrorResponse> handleNotFound(Exception exception, HttpServletRequest request) {
        return responseEntity(HttpStatus.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception) {
        return responseEntity(HttpStatus.METHOD_NOT_ALLOWED, "Request method not supported");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return responseEntity(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to process request");
    }

    private boolean isApiSurface(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith(request.getContextPath() + "/api/");
    }

    private ResponseEntity<ErrorResponse> responseEntity(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(message, status.value(), Instant.now()));
    }
}
