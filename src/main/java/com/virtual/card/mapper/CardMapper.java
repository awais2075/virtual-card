package com.virtual.card.mapper;

import com.virtual.card.dto.CardRequest;
import com.virtual.card.dto.CardResponse;
import com.virtual.card.enums.CardStatus;
import com.virtual.card.enums.CardSubStatus;
import com.virtual.card.model.Card;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class CardMapper {

    @Value("${card.expiry.years}")
    private int expiryYears;

    public Card toEntity(CardRequest request) {
        var card = new Card();
        card.setCardholderName(request.name().trim().toLowerCase());
        card.setBalance(request.initBalance());
        card.setExpiryDate(LocalDate.now().plusYears(expiryYears));
        card.setStatus(CardStatus.ACTIVE);
        card.setSubStatus(CardSubStatus.IN_USE);
        return card;
    }

    public CardResponse toResponse(Card card) {
        return new CardResponse(
                card.getId(),
                card.getCardholderName(),
                card.getBalance(),
                card.getExpiryDate(),
                card.getStatus(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }

}