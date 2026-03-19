package com.virtual.card.repository;

import com.virtual.card.model.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {


    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByCardIdAndIdempotencyKey(UUID cardId, String idempotencyKey);

    List<Transaction> findByCardId(UUID cardId, Pageable pageable);
}