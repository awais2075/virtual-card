package com.virtual.card.service;

import com.virtual.card.dto.CardRequest;
import com.virtual.card.dto.CardResponse;
import com.virtual.card.dto.TransactionRequest;
import com.virtual.card.dto.TransactionResponse;
import com.virtual.card.enums.CardStatus;
import com.virtual.card.enums.CardSubStatus;
import com.virtual.card.enums.TransactionStatus;
import com.virtual.card.enums.TransactionType;
import com.virtual.card.exception.CardFrozenException;
import com.virtual.card.exception.CardNotFoundException;
import com.virtual.card.exception.DuplicateCardException;
import com.virtual.card.exception.InsufficientFundsException;
import com.virtual.card.exception.InvalidAmountException;
import com.virtual.card.mapper.CardMapper;
import com.virtual.card.mapper.TransactionMapper;
import com.virtual.card.model.Card;
import com.virtual.card.model.Transaction;
import com.virtual.card.repository.CardRepository;
import com.virtual.card.repository.TransactionRepository;
import com.virtual.card.service.impl.CardServiceImpl;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private CardMapper cardMapper;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter mockCounter;

    @InjectMocks
    private CardServiceImpl cardService;

    private UUID cardId;

    @BeforeEach
    void stubMeterRegistry() {
        lenient().when(meterRegistry.counter(anyString())).thenReturn(mockCounter);
        cardId = UUID.randomUUID();
    }

    private Card activeCard(BigDecimal balance) {
        var card = new Card();
        card.setId(cardId);
        card.setCardholderName("john doe");
        card.setBalance(balance);
        card.setStatus(CardStatus.ACTIVE);
        card.setSubStatus(CardSubStatus.IN_USE);
        return card;
    }

    private CardResponse cardResponse(Card card) {
        return new CardResponse(card.getId(), card.getCardholderName(),
                card.getBalance(), null, card.getStatus(), null, null);
    }

    private TransactionResponse txResponse(UUID txId) {
        return new TransactionResponse(txId, cardId, TransactionType.CREDIT, TransactionStatus.SUCCESSFUL, BigDecimal.TEN, BigDecimal.valueOf(110), null);
    }

    @Nested
    class CreateCard {

        @Test
        void shouldCreateCard_withPositiveInitialBalance() {
            var request = new CardRequest("John Doe", BigDecimal.valueOf(100));
            var entity = activeCard(BigDecimal.valueOf(100));
            var response = cardResponse(entity);

            when(cardRepository.existsByCardholderNameAndStatus("john doe", CardStatus.ACTIVE)).thenReturn(false);
            when(cardMapper.toEntity(request)).thenReturn(entity);
            when(cardRepository.save(entity)).thenReturn(entity);
            when(cardMapper.toResponse(entity)).thenReturn(response);
            when(transactionService.recordTransaction(eq(entity), eq(BigDecimal.valueOf(100)),
                    anyString(), eq(TransactionType.CREDIT), eq(TransactionStatus.SUCCESSFUL)))
                    .thenReturn(new Transaction());

            var result = cardService.createCard(request);

            assertThat(result.id()).isEqualTo(cardId);
            assertThat(result.cardHolderName()).isEqualTo("john doe");
            assertThat(result.balance()).isEqualByComparingTo("100");
            // initial top-up transaction must be recorded
            verify(transactionService).recordTransaction(eq(entity), eq(BigDecimal.valueOf(100)),
                    anyString(), eq(TransactionType.CREDIT), eq(TransactionStatus.SUCCESSFUL));
            verify(mockCounter).increment();
        }

        @Test
        void shouldCreateCard_withZeroInitialBalance_andNoInitialTransaction() {
            var request = new CardRequest("Jane Smith", BigDecimal.ZERO);
            var entity = activeCard(BigDecimal.ZERO);
            var response = cardResponse(entity);

            when(cardRepository.existsByCardholderNameAndStatus("jane smith", CardStatus.ACTIVE)).thenReturn(false);
            when(cardMapper.toEntity(request)).thenReturn(entity);
            when(cardRepository.save(entity)).thenReturn(entity);
            when(cardMapper.toResponse(entity)).thenReturn(response);

            cardService.createCard(request);

            // balance is zero — no initial transaction should be recorded
            verify(transactionService, never()).recordTransaction(any(), any(), any(), any(), any());
        }

        @Test
        void shouldNormaliseName_toLowerCaseBeforeDuplicateCheck() {
            // "  ALICE  " must be checked as "alice" — tests trim + lowercase
            var request = new CardRequest("  ALICE  ", BigDecimal.valueOf(50));
            var entity = activeCard(BigDecimal.valueOf(50));

            when(cardRepository.existsByCardholderNameAndStatus("alice", CardStatus.ACTIVE)).thenReturn(false);
            when(cardMapper.toEntity(request)).thenReturn(entity);
            when(cardRepository.save(entity)).thenReturn(entity);
            when(cardMapper.toResponse(entity)).thenReturn(cardResponse(entity));
            when(transactionService.recordTransaction(any(), any(), any(), any(), any()))
                    .thenReturn(new Transaction());

            cardService.createCard(request);

            verify(cardRepository).existsByCardholderNameAndStatus("alice", CardStatus.ACTIVE);
        }

        @Test
        void shouldThrow_DuplicateCardException_whenActiveCardExists() {
            var request = new CardRequest("John Doe", BigDecimal.valueOf(100));
            when(cardRepository.existsByCardholderNameAndStatus("john doe", CardStatus.ACTIVE)).thenReturn(true);

            assertThatThrownBy(() -> cardService.createCard(request))
                    .isInstanceOf(DuplicateCardException.class)
                    .hasMessageContaining("john doe");

            verify(cardRepository, never()).save(any());
        }

        @Test
        void shouldThrow_InvalidAmountException_whenInitialBalanceIsNegative() {
            CardRequest request = new CardRequest("John Doe", BigDecimal.valueOf(-1));
            when(cardRepository.existsByCardholderNameAndStatus("john doe", CardStatus.ACTIVE)).thenReturn(false);

            assertThatThrownBy(() -> cardService.createCard(request))
                    .isInstanceOf(InvalidAmountException.class);

            verify(cardRepository, never()).save(any());
        }
    }

    @Nested
    class Details {

        @Test
        void shouldReturnCardResponse_whenActiveCardExists() {
            var card = activeCard(BigDecimal.valueOf(250));
            when(cardRepository.findByIdAndStatus(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));
            when(cardMapper.toResponse(card)).thenReturn(cardResponse(card));

            var result = cardService.details(cardId);

            assertThat(result.id()).isEqualTo(cardId);
            assertThat(result.balance()).isEqualByComparingTo("250");
        }

        @Test
        void shouldThrow_CardNotFoundException_whenCardDoesNotExist() {
            when(cardRepository.findByIdAndStatus(cardId, CardStatus.ACTIVE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.details(cardId))
                    .isInstanceOf(CardNotFoundException.class)
                    .hasMessageContaining(cardId.toString());
        }

        @Test
        void shouldThrow_CardNotFoundException_whenCardIsNotActive() {
            // Blocked / closed / expired cards are not found via findByIdAndStatus(ACTIVE)
            when(cardRepository.findByIdAndStatus(cardId, CardStatus.ACTIVE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.details(cardId))
                    .isInstanceOf(CardNotFoundException.class);
        }
    }

    @Nested
    class Credit {

        private final TransactionRequest request = new TransactionRequest(BigDecimal.valueOf(50), "idem-key-1");

        @Test
        void shouldCreditCard_andUpdateBalance() {
            var card = activeCard(BigDecimal.valueOf(100));
            var tx = new Transaction();
            tx.setId(UUID.randomUUID());
            TransactionResponse response = txResponse(tx.getId());

            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));
            when(transactionRepository.findByCardIdAndIdempotencyKey(cardId, "idem-key-1")).thenReturn(Optional.empty());
            when(transactionService.recordTransaction(card, BigDecimal.valueOf(50), "idem-key-1",
                    TransactionType.CREDIT, TransactionStatus.SUCCESSFUL)).thenReturn(tx);
            when(transactionMapper.toResponse(tx)).thenReturn(response);

            var result = cardService.credit(cardId, request);

            // balance must have been updated before save
            var cardCaptor = ArgumentCaptor.forClass(Card.class);
            verify(cardRepository).save(cardCaptor.capture());
            assertThat(cardCaptor.getValue().getBalance()).isEqualByComparingTo("150");

            assertThat(result).isEqualTo(response);
            verify(mockCounter).increment();
        }

        @Test
        void shouldReturnExistingTransaction_whenIdempotencyKeyAlreadyUsed() {
            var card = activeCard(BigDecimal.valueOf(100));
            var existingTx = new Transaction();
            var existingId = UUID.randomUUID();
            existingTx.setId(existingId);
            var existingResponse = txResponse(existingId);

            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));
            when(transactionRepository.findByCardIdAndIdempotencyKey(cardId, "idem-key-1"))
                    .thenReturn(Optional.of(existingTx));
            when(transactionMapper.toResponse(existingTx)).thenReturn(existingResponse);

            var result = cardService.credit(cardId, request);

            assertThat(result.id()).isEqualTo(existingId);
            // balance must NOT be changed on a duplicate request
            verify(cardRepository, never()).save(any());
            verify(transactionService, never()).recordTransaction(any(), any(), any(), any(), any());
        }

        @Test
        void shouldThrow_InvalidAmountException_whenAmountIsZero() {
            var zeroRequest = new TransactionRequest(BigDecimal.ZERO, "idem-key-2");

            assertThatThrownBy(() -> cardService.credit(cardId, zeroRequest))
                    .isInstanceOf(InvalidAmountException.class);

            verify(cardRepository, never()).findByIdAndStatusWithLock(any(), any());
        }

        @Test
        void shouldThrow_InvalidAmountException_whenAmountIsNegative() {
            var negRequest = new TransactionRequest(BigDecimal.valueOf(-10), "idem-key-3");

            assertThatThrownBy(() -> cardService.credit(cardId, negRequest))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        void shouldThrow_CardNotFoundException_whenCardNotActive() {
            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.credit(cardId, request))
                    .isInstanceOf(CardNotFoundException.class);
        }

        @Test
        void shouldThrow_CardFrozenException_whenCardIsFrozen() {
            var card = activeCard(BigDecimal.valueOf(100));
            card.setSubStatus(CardSubStatus.FROZEN);

            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> cardService.credit(cardId, request))
                    .isInstanceOf(CardFrozenException.class);

            verify(transactionRepository, never()).findByCardIdAndIdempotencyKey(any(), any());
            verify(cardRepository, never()).save(any());
        }

        @Test
        void shouldCredit_exactlyOneUnit_toVerifyPrecision() {
            // Verifies BigDecimal arithmetic — not float rounding
            var pennyRequest = new TransactionRequest(new BigDecimal("0.01"), "penny-key");
            var card = activeCard(new BigDecimal("99.99"));
            var tx = new Transaction();
            tx.setId(UUID.randomUUID());

            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));
            when(transactionRepository.findByCardIdAndIdempotencyKey(cardId, "penny-key")).thenReturn(Optional.empty());
            when(transactionService.recordTransaction(any(), any(), any(), any(), any())).thenReturn(tx);
            when(transactionMapper.toResponse(tx)).thenReturn(txResponse(tx.getId()));

            cardService.credit(cardId, pennyRequest);

            ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
            verify(cardRepository).save(captor.capture());
            assertThat(captor.getValue().getBalance()).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    class Debit {

        private final TransactionRequest request = new TransactionRequest(BigDecimal.valueOf(50), "idem-key-debit-1");

        @Test
        void shouldDebitCard_andUpdateBalance() {
            var card = activeCard(BigDecimal.valueOf(200));
            var tx = new Transaction();
            tx.setId(UUID.randomUUID());
            var response = new TransactionResponse(tx.getId(), cardId,
                    TransactionType.DEBIT, TransactionStatus.SUCCESSFUL, BigDecimal.valueOf(50), BigDecimal.valueOf(150), null);

            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));
            when(transactionRepository.findByCardIdAndIdempotencyKey(cardId, "idem-key-debit-1")).thenReturn(Optional.empty());
            when(transactionService.recordTransaction(card, BigDecimal.valueOf(50), "idem-key-debit-1",
                    TransactionType.DEBIT, TransactionStatus.SUCCESSFUL)).thenReturn(tx);
            when(transactionMapper.toResponse(tx)).thenReturn(response);

            var result = cardService.debit(cardId, request);

            ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
            verify(cardRepository).save(captor.capture());
            assertThat(captor.getValue().getBalance()).isEqualByComparingTo("150");
            assertThat(result.status()).isEqualTo(TransactionStatus.SUCCESSFUL);
            verify(mockCounter).increment();
        }

        @Test
        void shouldDebit_entireBalance_leavingZero() {
            var card = activeCard(BigDecimal.valueOf(100));
            var exactRequest = new TransactionRequest(BigDecimal.valueOf(100), "exact-key");
            var tx = new Transaction();
            tx.setId(UUID.randomUUID());

            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));
            when(transactionRepository.findByCardIdAndIdempotencyKey(cardId, "exact-key")).thenReturn(Optional.empty());
            when(transactionService.recordTransaction(any(), any(), any(), any(), any())).thenReturn(tx);
            when(transactionMapper.toResponse(tx)).thenReturn(
                    new TransactionResponse(tx.getId(), cardId, TransactionType.DEBIT,
                            TransactionStatus.SUCCESSFUL, BigDecimal.valueOf(100), BigDecimal.ZERO, null));

            var result = cardService.debit(cardId, exactRequest);

            var captor = ArgumentCaptor.forClass(Card.class);
            verify(cardRepository).save(captor.capture());
            // balance must be exactly 0, not negative
            assertThat(captor.getValue().getBalance()).isEqualByComparingTo("0");
            assertThat(result.status()).isEqualTo(TransactionStatus.SUCCESSFUL);
        }

        @Test
        void shouldReturnExistingTransaction_whenIdempotencyKeyAlreadyUsed() {
            var card = activeCard(BigDecimal.valueOf(200));
            var existingTx = new Transaction();
            var existingId = UUID.randomUUID();
            existingTx.setId(existingId);
            var existingResponse = new TransactionResponse(existingId, cardId,
                    TransactionType.DEBIT, TransactionStatus.SUCCESSFUL, BigDecimal.valueOf(50), BigDecimal.valueOf(150), null);

            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));
            when(transactionRepository.findByCardIdAndIdempotencyKey(cardId, "idem-key-debit-1"))
                    .thenReturn(Optional.of(existingTx));
            when(transactionMapper.toResponse(existingTx)).thenReturn(existingResponse);

            var result = cardService.debit(cardId, request);

            assertThat(result.id()).isEqualTo(existingId);
            verify(cardRepository, never()).save(any());
            verify(transactionService, never()).recordTransaction(any(), any(), any(), any(), any());
        }

        @Test
        void shouldThrow_InsufficientFundsException_andRecord_DeclinedTransaction() {
            var card = activeCard(BigDecimal.valueOf(30));
            var declinedTx = new Transaction();
            declinedTx.setId(UUID.randomUUID());

            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));
            when(transactionRepository.findByCardIdAndIdempotencyKey(cardId, "idem-key-debit-1")).thenReturn(Optional.empty());
            when(transactionService.recordTransaction(card, BigDecimal.valueOf(50), "idem-key-debit-1",
                    TransactionType.DEBIT, TransactionStatus.DECLINED)).thenReturn(declinedTx);

            assertThatThrownBy(() -> cardService.debit(cardId, request))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("30")   // shows current balance
                    .hasMessageContaining("50");  // shows requested amount

            // declined transaction must be written for audit trail
            verify(transactionService).recordTransaction(card, BigDecimal.valueOf(50), "idem-key-debit-1",
                    TransactionType.DEBIT, TransactionStatus.DECLINED);
            // balance must NOT be changed
            verify(cardRepository, never()).save(any());
            // declined counter must be incremented
            verify(mockCounter).increment();
        }

        @Test
        void shouldThrow_InsufficientFundsException_whenBalanceIsZero() {
            var card = activeCard(BigDecimal.ZERO);
            var declinedTx = new Transaction();
            declinedTx.setId(UUID.randomUUID());

            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));
            when(transactionRepository.findByCardIdAndIdempotencyKey(cardId, "idem-key-debit-1")).thenReturn(Optional.empty());
            when(transactionService.recordTransaction(any(), any(), any(), eq(TransactionType.DEBIT), eq(TransactionStatus.DECLINED)))
                    .thenReturn(declinedTx);

            assertThatThrownBy(() -> cardService.debit(cardId, request))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        void shouldThrow_InvalidAmountException_whenAmountIsZero() {
            var zeroRequest = new TransactionRequest(BigDecimal.ZERO, "idem-zero");

            assertThatThrownBy(() -> cardService.debit(cardId, zeroRequest))
                    .isInstanceOf(InvalidAmountException.class);

            verify(cardRepository, never()).findByIdAndStatusWithLock(any(), any());
        }

        @Test
        void shouldThrow_InvalidAmountException_whenAmountIsNegative() {
            var negRequest = new TransactionRequest(BigDecimal.valueOf(-5), "idem-neg");

            assertThatThrownBy(() -> cardService.debit(cardId, negRequest))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        void shouldThrow_CardNotFoundException_whenCardNotActive() {
            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.debit(cardId, request))
                    .isInstanceOf(CardNotFoundException.class)
                    .hasMessageContaining(cardId.toString());
        }

        @Test
        void shouldThrow_CardFrozenException_whenCardIsFrozen() {
            var card = activeCard(BigDecimal.valueOf(200));
            card.setSubStatus(CardSubStatus.FROZEN);

            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> cardService.debit(cardId, request))
                    .isInstanceOf(CardFrozenException.class);

            verify(cardRepository, never()).save(any());
            verify(transactionService, never()).recordTransaction(any(), any(), any(), any(), any());
        }

        @Test
        void shouldNotAllowBalanceToGoBelowZero_byOneUnit() {
            // balance = 49.99, request = 50.00 — must decline, not allow -0.01
            var card = activeCard(new BigDecimal("49.99"));
            var overRequest = new TransactionRequest(new BigDecimal("50.00"), "over-key");
            Transaction declinedTx = new Transaction();
            declinedTx.setId(UUID.randomUUID());

            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));
            when(transactionRepository.findByCardIdAndIdempotencyKey(cardId, "over-key")).thenReturn(Optional.empty());
            when(transactionService.recordTransaction(any(), any(), any(), eq(TransactionType.DEBIT), eq(TransactionStatus.DECLINED)))
                    .thenReturn(declinedTx);

            assertThatThrownBy(() -> cardService.debit(cardId, overRequest))
                    .isInstanceOf(InsufficientFundsException.class);

            verify(cardRepository, never()).save(any());
        }

        @Test
        void shouldRecordDeclinedTransaction_beforeThrowingException_soAuditIsNotLost() {
            // Validates ordering: declined tx is written BEFORE the exception propagates
            var card = activeCard(BigDecimal.valueOf(10));
            var declinedTx = new Transaction();
            declinedTx.setId(UUID.randomUUID());

            when(cardRepository.findByIdAndStatusWithLock(cardId, CardStatus.ACTIVE)).thenReturn(Optional.of(card));
            when(transactionRepository.findByCardIdAndIdempotencyKey(cardId, "idem-key-debit-1")).thenReturn(Optional.empty());
            when(transactionService.recordTransaction(any(), any(), any(), eq(TransactionType.DEBIT), eq(TransactionStatus.DECLINED)))
                    .thenReturn(declinedTx);

            assertThatThrownBy(() -> cardService.debit(cardId, request))
                    .isInstanceOf(InsufficientFundsException.class);

            // recordTransaction must have been called exactly once with DECLINED
            verify(transactionService, times(1)).recordTransaction(
                    eq(card), eq(BigDecimal.valueOf(50)), eq("idem-key-debit-1"),
                    eq(TransactionType.DEBIT), eq(TransactionStatus.DECLINED));
        }
    }
}