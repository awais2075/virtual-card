package com.virtual.card.service;

import com.virtual.card.dto.CardRequest;
import com.virtual.card.dto.CardResponse;
import com.virtual.card.dto.TransactionRequest;
import com.virtual.card.dto.TransactionResponse;

import java.util.UUID;

public interface CardService {

    CardResponse createCard(CardRequest request);
    CardResponse details(UUID cardId);
    TransactionResponse credit(UUID cardId, TransactionRequest request);
    TransactionResponse debit(UUID cardId, TransactionRequest request);
}
