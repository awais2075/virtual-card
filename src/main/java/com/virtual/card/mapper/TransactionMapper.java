package com.virtual.card.mapper;

import com.virtual.card.dto.TransactionResponse;
import com.virtual.card.model.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
            transaction.getId(),
            transaction.getCardId(),
            transaction.getType(),
            transaction.getStatus(),
            transaction.getAmount(),
            transaction.getBalanceAfter(),
            transaction.getCreatedAt()
        );
    }
}