package com.virtual.card.repository;

import com.virtual.card.enums.CardStatus;
import com.virtual.card.model.Card;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Card c WHERE c.id = :id AND c.status = :status")
    Optional<Card> findByIdAndStatusWithLock(@Param("id") UUID id, @Param("status") CardStatus status);

    Optional<Card> findByIdAndStatus(UUID id, CardStatus status);

    boolean existsByCardholderNameAndStatus(String cardholderName, CardStatus status);

    List<Card> findByStatusAndExpiryDateBefore(CardStatus status, LocalDate expiryDate);

}