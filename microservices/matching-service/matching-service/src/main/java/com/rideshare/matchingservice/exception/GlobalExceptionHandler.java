package com.rideshare.matchingservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoDriverAvailableException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNoDriverAvailable(NoDriverAvailableException ex) {
        return Map.of(
                "message", ex.getMessage(),
                "status", 404,
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
