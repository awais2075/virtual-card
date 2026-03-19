package com.virtual.card.controller;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.virtual.card.AbstractPostgresContainerTest;
import com.virtual.card.dto.CardRequest;
import com.virtual.card.dto.CardResponse;
import com.virtual.card.dto.TransactionRequest;
import com.virtual.card.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CardIntegrationTest extends AbstractPostgresContainerTest {

    @Autowired MockMvc mockMvc;

    // Instantiated directly — avoids ObjectMapper bean resolution issues
    // across different Spring Boot versions. JavaTimeModule handles
    // Instant serialisation used in CardResponse and TransactionResponse.
    private ObjectMapper objectMapper;

    private static final String BASE = "/api/v1/cards";

    @BeforeEach
    void setUpObjectMapper() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CardResponse createCard(String name, BigDecimal balance) throws Exception {
        var req = new CardRequest(name, balance);
        var result = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), CardResponse.class);
    }

    private TransactionResponse credit(UUID cardId, BigDecimal amount, String key) throws Exception {
        var req = new TransactionRequest(amount, key);
        var result = mockMvc.perform(post(BASE + "/" + cardId + "/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TransactionResponse.class);
    }

    private TransactionResponse debit(UUID cardId, BigDecimal amount, String key) throws Exception {
        var req = new TransactionRequest(amount, key);
        var result = mockMvc.perform(post(BASE + "/" + cardId + "/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TransactionResponse.class);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void fullLifecycle_createCreditDebit_balanceIsCorrect() throws Exception {
        var card = createCard("Alice", BigDecimal.valueOf(100));

        credit(card.id(), BigDecimal.valueOf(50), "key-topup-1");

        debit(card.id(), BigDecimal.valueOf(80), "key-spend-1");

        // balance: 100 + 50 - 80 = 70
        mockMvc.perform(get(BASE + "/" + card.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(70));
    }

    @Test
    void createCard_returns201_withCorrectFields() throws Exception {
        var req = new CardRequest("Bob", BigDecimal.valueOf(200));
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardHolderName").value("bob"))
                .andExpect(jsonPath("$.balance").value(200))
                .andExpect(jsonPath("$.cardStatus").value("ACTIVE"));
    }

    @Test
    void createCard_rejects_duplicateActiveName() throws Exception {
        createCard("Charlie", BigDecimal.valueOf(100));

        var dup = new CardRequest("Charlie", BigDecimal.valueOf(50));
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void debit_returns422_whenInsufficientFunds() throws Exception {
        var card = createCard("Dave", BigDecimal.valueOf(30));

        var req = new TransactionRequest(BigDecimal.valueOf(50), "key-over");
        mockMvc.perform(post(BASE + "/" + card.id() + "/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void credit_isIdempotent_withSameKey() throws Exception {
        var card = createCard("Eve", BigDecimal.valueOf(100));

        credit(card.id(), BigDecimal.valueOf(50), "same-key");
        credit(card.id(), BigDecimal.valueOf(50), "same-key"); // duplicate

        // balance must be 150, not 200
        mockMvc.perform(get(BASE + "/" + card.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150));
    }

    @Test
    void debit_isIdempotent_withSameKey() throws Exception {
        var card = createCard("Frank", BigDecimal.valueOf(200));

        debit(card.id(), BigDecimal.valueOf(50), "debit-same-key");
        debit(card.id(), BigDecimal.valueOf(50), "debit-same-key"); // duplicate

        // balance must be 150, not 100
        mockMvc.perform(get(BASE + "/" + card.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150));
    }

    @Test
    void getCard_returns404_forUnknownId() throws Exception {
        mockMvc.perform(get(BASE + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransactions_returnsHistory_inDescendingOrder() throws Exception {
        var card = createCard("Grace", BigDecimal.valueOf(500));

        credit(card.id(), BigDecimal.valueOf(100), "tx-key-1");
        debit(card.id(), BigDecimal.valueOf(40), "tx-key-2");

        mockMvc.perform(get(BASE + "/" + card.id() + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)) // initial + credit + debit
                .andExpect(jsonPath("$[0].type").value("DEBIT")); // newest first
    }

    @Test
    void validation_rejects_blankCardholderName() throws Exception {
        var req = new CardRequest("", BigDecimal.valueOf(100));
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void validation_rejects_missingIdempotencyKey() throws Exception {
        var card = createCard("Henry", BigDecimal.valueOf(100));

        var body = "{\"amount\": 10}";
        mockMvc.perform(post(BASE + "/" + card.id() + "/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}