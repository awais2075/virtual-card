package com.virtual.card.service;

import com.virtual.card.dto.TransactionResponse;
import com.virtual.card.enums.TransactionStatus;
import com.virtual.card.enums.TransactionType;
import com.virtual.card.model.Card;
import com.virtual.card.model.Transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TransactionService {

    List<TransactionResponse> transactions(UUID cardId, int page, int size);
    Transaction recordTransaction(Card card, BigDecimal amount, String idempotencyKey, TransactionType type, TransactionStatus status);
}
