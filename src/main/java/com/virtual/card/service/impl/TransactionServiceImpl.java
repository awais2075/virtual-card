package com.virtual.card.service.impl;

import com.virtual.card.dto.TransactionResponse;
import com.virtual.card.enums.TransactionStatus;
import com.virtual.card.enums.TransactionType;
import com.virtual.card.exception.CardNotFoundException;
import com.virtual.card.mapper.TransactionMapper;
import com.virtual.card.model.Card;
import com.virtual.card.model.Transaction;
import com.virtual.card.repository.CardRepository;
import com.virtual.card.repository.TransactionRepository;
import com.virtual.card.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class TransactionServiceImpl implements TransactionService {

    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public TransactionServiceImpl(CardRepository cardRepository, TransactionRepository transactionRepository, TransactionMapper transactionMapper) {
        this.cardRepository = cardRepository;
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> transactions(UUID cardId, int page, int size) {
        log.info("Fetching transactions for cardId: {}, page: {}, size: {}", cardId, page, size);

        if (!cardRepository.existsById(cardId)) {
            throw new CardNotFoundException("No record found for cardId: '%s'".formatted(cardId));
        }

        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        var transactions = transactionRepository.findByCardId(cardId, pageable)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();

        log.info("Found {} transactions for cardId: {} on page {}", transactions.size(), cardId, page);

        return transactions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction recordTransaction(Card card, BigDecimal amount, String idempotencyKey, TransactionType type, TransactionStatus status) {

        var transaction = new Transaction();
        transaction.setCardId(card.getId());
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setBalanceAfter(card.getBalance());

        return transactionRepository.save(transaction);
    }
}
