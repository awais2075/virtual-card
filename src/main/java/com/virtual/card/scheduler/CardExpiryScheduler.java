package com.virtual.card.scheduler;

import com.virtual.card.enums.CardStatus;
import com.virtual.card.enums.CardSubStatus;
import com.virtual.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardExpiryScheduler {

    private final CardRepository cardRepository;

    @Scheduled(cron = "${card.expiry.cron:0 0 0 * * *}")
    @Transactional
    public void expireCards() {
        log.info("Card expiry job triggered at: {}", LocalDate.now());

        var expiredCards = cardRepository
                .findByStatusAndExpiryDateBefore(CardStatus.ACTIVE, LocalDate.now());

        if (expiredCards.isEmpty()) {
            log.info("No cards to expire");
            return;
        }

        expiredCards.forEach(card -> {
            log.info("Expiring card: {}", card.getId());
            card.setStatus(CardStatus.EXPIRED);
            card.setSubStatus(CardSubStatus.EXPIRED_NATURAL);
            card.setUpdatedBy("scheduler-system");
        });

        cardRepository.saveAll(expiredCards);
        log.info("Expired {} cards", expiredCards.size());
    }
}