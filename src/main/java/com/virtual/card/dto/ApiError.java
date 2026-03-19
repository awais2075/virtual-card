package com.virtual.card.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final String message;
    private final int status;
    private final Instant timestamp;
    private final List<FieldError> fieldErrors;

    public ApiError(String message, int status, Instant timestamp) {
        this.message = message;
        this.status = status;
        this.timestamp = timestamp;
        this.fieldErrors = null;
    }

    public ApiError(String message, int status, Instant timestamp, List<FieldError> fieldErrors) {
        this.message = message;
        this.status = status;
        this.timestamp = timestamp;
        this.fieldErrors = fieldErrors;
    }

    public String getMessage() { return message; }
    public int getStatus() { return status; }
    public Instant getTimestamp() { return timestamp; }
    public List<FieldError> getFieldErrors() { return fieldErrors; }

    public static class FieldError {

        private final String field;
        private final String message;
        private final Object rejectedValue;

        public FieldError(String field, String message, Object rejectedValue) {
            this.field = field;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }

        public String getField() { return field; }
        public String getMessage() { return message; }
        public Object getRejectedValue() { return rejectedValue; }
    }
}