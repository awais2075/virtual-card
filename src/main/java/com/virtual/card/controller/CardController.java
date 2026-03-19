package com.virtual.card.controller;

import com.virtual.card.dto.CardRequest;
import com.virtual.card.dto.CardResponse;
import com.virtual.card.dto.TransactionRequest;
import com.virtual.card.dto.TransactionResponse;
import com.virtual.card.service.CardService;
import com.virtual.card.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = "Cards", description = "Virtual card management endpoints")
public class CardController {

    private final CardService cardService;
    private final TransactionService transactionService;

    public CardController(CardService cardService, TransactionService transactionService) {
        this.cardService = cardService;
        this.transactionService = transactionService;
    }

    @Operation(summary = "Create a new virtual card")
    @PostMapping
    public ResponseEntity<CardResponse> createCard(@Valid @RequestBody CardRequest request) {
        log.info("Create card request for cardholder: {}", request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(cardService.createCard(request));
    }

    @Operation(summary = "Get active card details")
    @GetMapping("/{cardId}")
    public ResponseEntity<CardResponse> cardDetails(@PathVariable UUID cardId) {
        log.info("Get card details request for cardId: {}", cardId);
        return ResponseEntity.ok(cardService.details(cardId));
    }

    @Operation(summary = "Top up a card")
    @PostMapping("/{cardId}/credit")
    public ResponseEntity<TransactionResponse> credit(@PathVariable UUID cardId, @Valid @RequestBody TransactionRequest request) {
        log.info("Credit request for cardId: {}", cardId);
        return ResponseEntity.ok(cardService.credit(cardId, request));
    }

    @Operation(summary = "Spend from a card")
    @PostMapping("/{cardId}/debit")
    public ResponseEntity<TransactionResponse> debit(@PathVariable UUID cardId, @Valid @RequestBody TransactionRequest request) {
        log.info("Debit request for cardId: {}", cardId);
        return ResponseEntity.ok(cardService.debit(cardId, request));
    }

    @Operation(summary = "Get transaction history for a card")
    @GetMapping("/{cardId}/transactions")
    public ResponseEntity<List<TransactionResponse>> transactions(@PathVariable UUID cardId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        log.info("Get transactions request for cardId: {}, page: {}, size: {}", cardId, page, size);
        return ResponseEntity.ok(transactionService.transactions(cardId, page, size));
    }
}