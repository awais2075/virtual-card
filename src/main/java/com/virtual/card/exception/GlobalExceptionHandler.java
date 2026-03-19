package com.virtual.card.exception;

import com.virtual.card.dto.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateCardException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleDuplicateCard(DuplicateCardException ex) {
        return new ApiError(ex.getMessage(), HttpStatus.BAD_REQUEST.value(), Instant.now());
    }

    @ExceptionHandler(InvalidAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidAmount(InvalidAmountException ex) {
        return new ApiError(ex.getMessage(), HttpStatus.BAD_REQUEST.value(), Instant.now());
    }

    @ExceptionHandler(CardNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleCardNotFound(CardNotFoundException ex) {
        return new ApiError(ex.getMessage(), HttpStatus.NOT_FOUND.value(), Instant.now());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInsufficientFunds(InsufficientFundsException ex) {
        return new ApiError(ex.getMessage(), HttpStatus.BAD_REQUEST.value(), Instant.now());
    }

    @ExceptionHandler(CardNotActiveException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleCardNotActive(CardNotActiveException ex) {
        return new ApiError(ex.getMessage(), HttpStatus.BAD_REQUEST.value(), Instant.now());
    }

    @ExceptionHandler(CardFrozenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleCardFrozen(CardFrozenException ex) {
        return new ApiError(ex.getMessage(), HttpStatus.BAD_REQUEST.value(), Instant.now());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidationErrors(MethodArgumentNotValidException ex) {
        var fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(err -> new ApiError.FieldError(err.getField(), err.getDefaultMessage(), err.getRejectedValue()))
                .toList();

        return new ApiError("Request validation failed", HttpStatus.BAD_REQUEST.value(), Instant.now(), fieldErrors);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return new ApiError("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR.value(), Instant.now());
    }
}
