package com.virtual.card.service;

import com.virtual.card.dto.TransactionResponse;
import com.virtual.card.enums.TransactionStatus;
import com.virtual.card.enums.TransactionType;
import com.virtual.card.exception.CardNotFoundException;
import com.virtual.card.mapper.TransactionMapper;
import com.virtual.card.model.Card;
import com.virtual.card.model.Transaction;
import com.virtual.card.repository.CardRepository;
import com.virtual.card.repository.TransactionRepository;
import com.virtual.card.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private UUID cardId;

    @BeforeEach
    void setup() {
        cardId = UUID.randomUUID();
    }


    private Transaction buildTx(UUID id, BigDecimal amount, BigDecimal balanceAfter, TransactionType type, TransactionStatus status, String key) {
        var tx = new Transaction();
        tx.setId(id);
        tx.setCardId(cardId);
        tx.setAmount(amount);
        tx.setBalanceAfter(balanceAfter);
        tx.setType(type);
        tx.setStatus(status);
        tx.setIdempotencyKey(key);
        return tx;
    }

    private TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(tx.getId(), tx.getCardId(), tx.getType(),
                tx.getStatus(), tx.getAmount(), tx.getBalanceAfter(), Instant.now());
    }

    private Card cardWithBalance(BigDecimal balance) {
        var card = new Card();
        card.setId(cardId);
        card.setBalance(balance);
        return card;
    }

    @Nested
    class Transactions {

        @Test
        void shouldReturnMappedTransactions_forFirstPage() {
            var tx = buildTx(UUID.randomUUID(), BigDecimal.valueOf(100),
                    BigDecimal.valueOf(900), TransactionType.DEBIT, TransactionStatus.SUCCESSFUL, "k1");
            var response = toResponse(tx);

            when(cardRepository.existsById(cardId)).thenReturn(true);
            when(transactionRepository.findByCardId(eq(cardId), any(PageRequest.class))).thenReturn(List.of(tx));
            when(transactionMapper.toResponse(tx)).thenReturn(response);

            var result = transactionService.transactions(cardId, 0, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(tx.getId());
            assertThat(result.get(0).amount()).isEqualByComparingTo("100");
            assertThat(result.get(0).balanceAfter()).isEqualByComparingTo("900");
        }

        @Test
        void shouldReturnEmptyList_whenCardHasNoTransactions() {
            when(cardRepository.existsById(cardId)).thenReturn(true);
            when(transactionRepository.findByCardId(eq(cardId), any(PageRequest.class)))
                    .thenReturn(Collections.emptyList());

            var result = transactionService.transactions(cardId, 0, 10);

            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        void shouldThrow_CardNotFoundException_whenCardDoesNotExist() {
            when(cardRepository.existsById(cardId)).thenReturn(false);

            assertThatThrownBy(() -> transactionService.transactions(cardId, 0, 10))
                    .isInstanceOf(CardNotFoundException.class)
                    .hasMessageContaining(cardId.toString());
        }

        @Test
        void shouldPassCorrectPageable_toRepository() {
            when(cardRepository.existsById(cardId)).thenReturn(true);
            when(transactionRepository.findByCardId(eq(cardId), any(PageRequest.class)))
                    .thenReturn(Collections.emptyList());

            transactionService.transactions(cardId, 2, 5);

            var pageableCaptor = ArgumentCaptor.forClass(PageRequest.class);
            verify(transactionRepository).findByCardId(eq(cardId), pageableCaptor.capture());

            var captured = pageableCaptor.getValue();
            assertThat(captured.getPageNumber()).isEqualTo(2);
            assertThat(captured.getPageSize()).isEqualTo(5);
            // must sort newest first
            assertThat(captured.getSort()).isEqualTo(Sort.by("createdAt").descending());
        }

        @Test
        void shouldReturnCorrectSubset_forSecondPage() {
            // 25 total transactions — page 1 (second page) of size 10 returns items 10-19
            var allTxs = IntStream.range(0, 25)
                    .mapToObj(i -> buildTx(UUID.randomUUID(), BigDecimal.valueOf(i + 1),
                            BigDecimal.valueOf(1000 - i), TransactionType.CREDIT, TransactionStatus.SUCCESSFUL, "k" + i))
                    .toList();

            when(cardRepository.existsById(cardId)).thenReturn(true);
            when(transactionRepository.findByCardId(eq(cardId), any(PageRequest.class)))
                    .thenAnswer(inv -> {
                        PageRequest pr = inv.getArgument(1);
                        var start = (int) pr.getOffset();
                        var end = Math.min(start + pr.getPageSize(), allTxs.size());
                        return allTxs.subList(start, end);
                    });
            when(transactionMapper.toResponse(any(Transaction.class)))
                    .thenAnswer(inv -> toResponse(inv.getArgument(0)));

            var page1 = transactionService.transactions(cardId, 1, 10);

            assertThat(page1).hasSize(10);
            // first item of page 1 corresponds to allTxs[10] with amount = 11
            assertThat(page1.get(0).amount()).isEqualByComparingTo("11");
        }

        @Test
        void shouldReturnLastPartialPage_whenItemsDoNotFillPageSize() {
            // 25 items, page 2, size 10 → only 5 items remain
            var allTxs = IntStream.range(0, 25)
                    .mapToObj(i -> buildTx(UUID.randomUUID(), BigDecimal.ONE,
                            BigDecimal.TEN, TransactionType.DEBIT, TransactionStatus.SUCCESSFUL, "k" + i))
                    .toList();

            when(cardRepository.existsById(cardId)).thenReturn(true);
            when(transactionRepository.findByCardId(eq(cardId), any(PageRequest.class)))
                    .thenAnswer(inv -> {
                        PageRequest pr = inv.getArgument(1);
                        int start = (int) pr.getOffset();
                        int end = Math.min(start + pr.getPageSize(), allTxs.size());
                        return allTxs.subList(start, end);
                    });
            when(transactionMapper.toResponse(any())).thenAnswer(inv -> toResponse(inv.getArgument(0)));

            var lastPage = transactionService.transactions(cardId, 2, 10);

            assertThat(lastPage).hasSize(5);
        }

        @Test
        void shouldReturnEmptyList_whenPageExceedsTotalItems() {
            when(cardRepository.existsById(cardId)).thenReturn(true);
            when(transactionRepository.findByCardId(eq(cardId), any(PageRequest.class)))
                    .thenReturn(Collections.emptyList());

            var result = transactionService.transactions(cardId, 99, 10);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnMultipleTransactions_inOrderReturnedByRepository() {
            // Service does not sort — it trusts the repository/pageable order
            var tx1 = buildTx(UUID.randomUUID(), BigDecimal.valueOf(200),
                    BigDecimal.valueOf(800), TransactionType.CREDIT, TransactionStatus.SUCCESSFUL, "k1");
            var tx2 = buildTx(UUID.randomUUID(), BigDecimal.valueOf(50),
                    BigDecimal.valueOf(750), TransactionType.DEBIT, TransactionStatus.SUCCESSFUL, "k2");

            when(cardRepository.existsById(cardId)).thenReturn(true);
            when(transactionRepository.findByCardId(eq(cardId), any(PageRequest.class)))
                    .thenReturn(List.of(tx1, tx2));
            when(transactionMapper.toResponse(tx1)).thenReturn(toResponse(tx1));
            when(transactionMapper.toResponse(tx2)).thenReturn(toResponse(tx2));

            var result = transactionService.transactions(cardId, 0, 10);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(tx1.getId());
            assertThat(result.get(1).id()).isEqualTo(tx2.getId());
        }
    }

    @Nested
    class RecordTransaction {

        @Test
        void shouldSaveTransaction_withCorrectFields_forCredit() {
            var card = cardWithBalance(BigDecimal.valueOf(500));
            var saved = buildTx(UUID.randomUUID(), BigDecimal.valueOf(100),
                    BigDecimal.valueOf(500), TransactionType.CREDIT, TransactionStatus.SUCCESSFUL, "credit-key");

            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            var result = transactionService.recordTransaction(
                    card, BigDecimal.valueOf(100), "credit-key",
                    TransactionType.CREDIT, TransactionStatus.SUCCESSFUL);

            assertThat(result.getType()).isEqualTo(TransactionType.CREDIT);
            assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUCCESSFUL);
            assertThat(result.getAmount()).isEqualByComparingTo("100");
        }

        @Test
        void shouldSaveTransaction_withCorrectFields_forDebit() {
            var card = cardWithBalance(BigDecimal.valueOf(300));
            var saved = buildTx(UUID.randomUUID(), BigDecimal.valueOf(50),
                    BigDecimal.valueOf(300), TransactionType.DEBIT, TransactionStatus.SUCCESSFUL, "debit-key");

            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            var result = transactionService.recordTransaction(
                    card, BigDecimal.valueOf(50), "debit-key",
                    TransactionType.DEBIT, TransactionStatus.SUCCESSFUL);

            assertThat(result.getType()).isEqualTo(TransactionType.DEBIT);
            assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUCCESSFUL);
        }

        @Test
        void shouldSaveDeclinedTransaction_withDeclinedStatus() {
            var card = cardWithBalance(BigDecimal.valueOf(20));
            var saved = buildTx(UUID.randomUUID(), BigDecimal.valueOf(100),
                    BigDecimal.valueOf(20), TransactionType.DEBIT, TransactionStatus.DECLINED, "declined-key");

            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            var result = transactionService.recordTransaction(
                    card, BigDecimal.valueOf(100), "declined-key",
                    TransactionType.DEBIT, TransactionStatus.DECLINED);

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.DECLINED);
            assertThat(result.getType()).isEqualTo(TransactionType.DEBIT);
        }

        @Test
        void shouldSetBalanceAfter_fromCardBalanceAtCallTime() {
            // balanceAfter must reflect card.getBalance() at the moment of recording,
            // which is the NEW balance after the operation (set by CardServiceImpl before calling this)
            var card = cardWithBalance(new BigDecimal("150.75"));
            var txCaptor = ArgumentCaptor.forClass(Transaction.class);
            var saved = buildTx(UUID.randomUUID(), BigDecimal.TEN,
                    new BigDecimal("150.75"), TransactionType.CREDIT, TransactionStatus.SUCCESSFUL, "bal-key");

            when(transactionRepository.save(txCaptor.capture())).thenReturn(saved);

            transactionService.recordTransaction(card, BigDecimal.TEN, "bal-key",
                    TransactionType.CREDIT, TransactionStatus.SUCCESSFUL);

            assertThat(txCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("150.75");
        }

        @Test
        void shouldSetCardId_fromCard() {
            var card = cardWithBalance(BigDecimal.valueOf(200));
            var txCaptor = ArgumentCaptor.forClass(Transaction.class);
            var saved = buildTx(UUID.randomUUID(), BigDecimal.TEN,
                    BigDecimal.valueOf(200), TransactionType.CREDIT, TransactionStatus.SUCCESSFUL, "id-key");

            when(transactionRepository.save(txCaptor.capture())).thenReturn(saved);

            transactionService.recordTransaction(card, BigDecimal.TEN, "id-key",
                    TransactionType.CREDIT, TransactionStatus.SUCCESSFUL);

            assertThat(txCaptor.getValue().getCardId()).isEqualTo(cardId);
        }

        @Test
        void shouldSetIdempotencyKey_onSavedTransaction() {
            var card = cardWithBalance(BigDecimal.valueOf(100));
            var txCaptor = ArgumentCaptor.forClass(Transaction.class);
            var saved = buildTx(UUID.randomUUID(), BigDecimal.ONE,
                    BigDecimal.valueOf(100), TransactionType.DEBIT, TransactionStatus.SUCCESSFUL, "my-idem-key");

            when(transactionRepository.save(txCaptor.capture())).thenReturn(saved);

            transactionService.recordTransaction(card, BigDecimal.ONE, "my-idem-key",
                    TransactionType.DEBIT, TransactionStatus.SUCCESSFUL);

            assertThat(txCaptor.getValue().getIdempotencyKey()).isEqualTo("my-idem-key");
        }

        @Test
        void shouldReturnSavedEntity_fromRepository() {
            var card = cardWithBalance(BigDecimal.valueOf(500));
            var expectedId = UUID.randomUUID();
            var saved = buildTx(expectedId, BigDecimal.valueOf(50),
                    BigDecimal.valueOf(500), TransactionType.CREDIT, TransactionStatus.SUCCESSFUL, "ret-key");

            when(transactionRepository.save(any())).thenReturn(saved);

            var result = transactionService.recordTransaction(card, BigDecimal.valueOf(50), "ret-key",
                    TransactionType.CREDIT, TransactionStatus.SUCCESSFUL);

            assertThat(result.getId()).isEqualTo(expectedId);
        }
    }
}