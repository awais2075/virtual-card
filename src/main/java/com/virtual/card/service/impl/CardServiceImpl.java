package com.virtual.card.service.impl;

import com.virtual.card.dto.CardRequest;
import com.virtual.card.dto.CardResponse;
import com.virtual.card.dto.TransactionRequest;
import com.virtual.card.dto.TransactionResponse;
import com.virtual.card.enums.CardStatus;
import com.virtual.card.enums.CardSubStatus;
import com.virtual.card.enums.TransactionStatus;
import com.virtual.card.enums.TransactionType;
import com.virtual.card.exception.*;
import com.virtual.card.mapper.CardMapper;
import com.virtual.card.mapper.TransactionMapper;
import com.virtual.card.model.Card;
import com.virtual.card.model.Transaction;
import com.virtual.card.repository.CardRepository;
import com.virtual.card.repository.TransactionRepository;
import com.virtual.card.service.CardService;
import com.virtual.card.service.TransactionService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class CardServiceImpl implements CardService {


    private final TransactionService transactionService;
    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;
    private final CardMapper cardMapper;
    private final TransactionMapper transactionMapper;
    private final MeterRegistry meterRegistry;

    public CardServiceImpl(TransactionService transactionService, CardRepository cardRepository, TransactionRepository transactionRepository, CardMapper cardMapper, TransactionMapper transactionMapper, MeterRegistry meterRegistry) {
        this.transactionService = transactionService;
        this.cardRepository = cardRepository;
        this.transactionRepository = transactionRepository;
        this.cardMapper = cardMapper;
        this.transactionMapper = transactionMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Transactional
    public CardResponse createCard(CardRequest request) {
        log.info("Creating card for cardholder: {}", request.name());

        // 1. normalize
        var cardholderName = request.name().trim().toLowerCase();

        // 2. duplicate check
        if (cardRepository.existsByCardholderNameAndStatus(cardholderName, CardStatus.ACTIVE)) {
            throw new DuplicateCardException("Cardholder %s already has an active card".formatted(cardholderName));
        }

        // 3. validate balance
        if (request.initBalance().signum() < 0) {
            throw new InvalidAmountException("Card balance cannot be negative");
        }

        // 4. build card
        var card = cardMapper.toEntity(request);

        // 5. save
        var saved = cardRepository.save(card);

        // 6. record initial transaction
        if (request.initBalance().signum() > 0) {
            transactionService.recordTransaction(saved, request.initBalance(), UUID.randomUUID().toString(), TransactionType.CREDIT, TransactionStatus.SUCCESSFUL);
        }

        log.info("Card created: {} for cardholder: {}", saved.getId(), cardholderName);

        meterRegistry.counter("cards.created").increment();

        return cardMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CardResponse details(UUID cardId) {
        log.info("Fetching card details for cardId: {}", cardId);

        return cardRepository.findByIdAndStatus(cardId, CardStatus.ACTIVE)
                .map(cardMapper::toResponse)
                .orElseThrow(() -> {
                    log.error("No active card found for cardId: {}", cardId);
                    return new CardNotFoundException("No active card found for cardId: %s".formatted(cardId));
                });
    }


    @Override
    @Transactional
    public TransactionResponse credit(UUID cardId, TransactionRequest request) {
        log.info("Processing credit for cardId: {}, amount: {}", cardId, request.amount());

        // 1. validate amount
        if (request.amount().signum() <= 0) {
            throw new InvalidAmountException("Credit amount must be greater than zero");
        }

        // 2. fetch card
        var card = cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)
                .orElseThrow(() -> {
                    log.error("No active card found for cardId: {}", cardId);
                    return new CardNotFoundException("No active card found for cardId: %s".formatted(cardId));
                });

        if (card.getSubStatus() == CardSubStatus.FROZEN) {
            log.error("Can not perform Credit on Frozen Card");
            throw new CardFrozenException("Can not perform Credit on Frozen Card");
        }

        // 3. check idempotency
        var existing = transactionRepository.findByCardIdAndIdempotencyKey(cardId, request.idempotencyKey())
                .orElse(null);

        if (Objects.nonNull(existing)) {
            log.info("Duplicate credit request for cardId: {}, idempotencyKey: {}", cardId, request.idempotencyKey());

            meterRegistry.counter("transactions.idempotency.hit").increment();
            return transactionMapper.toResponse(existing);
        }

        // 4. update balance
        var newBalance = card.getBalance().add(request.amount());
        card.setBalance(newBalance);
        card.setUpdatedBy("system");
        cardRepository.save(card);

        // 5. record transaction
        var transaction = transactionService.recordTransaction(card, request.amount(), request.idempotencyKey(), TransactionType.CREDIT, TransactionStatus.SUCCESSFUL);

        log.info("Credit successful for cardId: {}, new balance: {}", card.getId(), newBalance);

        meterRegistry.counter("transactions.credit.success").increment();

        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse debit(UUID cardId, TransactionRequest request) {
        log.info("Processing debit for cardId: {}, amount: {}", cardId, request.amount());

        // 1. validate amount
        if (request.amount().signum() <= 0) {
            throw new InvalidAmountException("Debit amount must be greater than zero");
        }

        // 2. fetch card
        var card = cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)
                .orElseThrow(() -> {
                    log.error("No active card found for cardId: {}", cardId);
                    return new CardNotFoundException("No active card found for cardId: %s".formatted(cardId));
                });

        if (card.getSubStatus() == CardSubStatus.FROZEN) {
            log.error("Can not perform Debit on Frozen Card");
            throw new CardFrozenException("Can not perform Debit on Frozen Card");
        }

        // 3. check idempotency
        var existing = transactionRepository.findByCardIdAndIdempotencyKey(cardId, request.idempotencyKey())
                .orElse(null);

        if (Objects.nonNull(existing)) {
            log.info("Duplicate debit request for cardId: {}, idempotencyKey: {}", cardId, request.idempotencyKey());

            meterRegistry.counter("transactions.idempotency.hit").increment();
            return transactionMapper.toResponse(existing);
        }

        // 4. check sufficient funds
        var newBalance = card.getBalance().subtract(request.amount());

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.error("Insufficient funds for cardId: {}, balance: {}, requested: {}", card.getId(), card.getBalance(), request.amount());

            transactionService.recordTransaction(card, request.amount(), request.idempotencyKey(), TransactionType.DEBIT, TransactionStatus.DECLINED);
            meterRegistry.counter("transactions.debit.declined").increment();

            throw new InsufficientFundsException("Insufficient funds. Current balance: %s, requested: %s".formatted(card.getBalance(), request.amount()));
        }

        // 5. update balance
        card.setBalance(newBalance);
        card.setUpdatedBy("system");
        cardRepository.save(card);

        // 6. record transaction
        var transaction = transactionService.recordTransaction(card, request.amount(), request.idempotencyKey(), TransactionType.DEBIT, TransactionStatus.SUCCESSFUL);

        log.info("Debit successful for cardId: {}, new balance: {}", card.getId(), newBalance);

        meterRegistry.counter("transactions.debit.success").increment();

        return transactionMapper.toResponse(transaction);
    }
}
