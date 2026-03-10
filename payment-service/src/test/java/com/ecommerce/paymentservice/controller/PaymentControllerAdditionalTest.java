package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.BaseIntegrationTest;
import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerAdditionalTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    // ========== Health Controller ==========

    @Nested
    @DisplayName("GET /api/health")
    class HealthEndpoint {

        @Test
        @DisplayName("should return service status UP")
        void health_returnsStatus() throws Exception {
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.service").value("payment-service"))
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    // ========== Process Payment — Additional Methods ==========

    @Nested
    @DisplayName("POST /api/payments — additional methods")
    class ProcessPaymentAdditional {

        @Test
        @DisplayName("should process BANK_TRANSFER payment with ACH- prefix")
        void processPayment_bankTransfer() throws Exception {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(100L).userId(1L)
                    .amount(new BigDecimal("500.00"))
                    .paymentMethod("BANK_TRANSFER")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.paymentMethod").value("BANK_TRANSFER"))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            JsonNode node = objectMapper.readTree(json);
            String txnId = node.get("data").get("transactionId").asText();
            assertThat(txnId).startsWith("ACH-");
        }

        @Test
        @DisplayName("should process DEBIT_CARD payment with pi_ prefix via Stripe")
        void processPayment_debitCard() throws Exception {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(101L).userId(1L)
                    .amount(new BigDecimal("75.50"))
                    .paymentMethod("DEBIT_CARD")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.paymentMethod").value("DEBIT_CARD"))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            JsonNode node = objectMapper.readTree(json);
            String txnId = node.get("data").get("transactionId").asText();
            assertThat(txnId).startsWith("pi_");
        }
    }

    // ========== Get Payment — Non-existent Order ==========

    @Nested
    @DisplayName("GET /api/payments/order/{orderId}")
    class GetPaymentByOrder {

        @Test
        @DisplayName("should return 404 for non-existent order ID")
        void getByOrderId_notFound() throws Exception {
            mockMvc.perform(get("/api/payments/order/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Payment not found")));
        }
    }

    // ========== Boundary Validation ==========

    @Nested
    @DisplayName("POST /api/payments — boundary validation")
    class BoundaryValidation {

        @Test
        @DisplayName("should accept payment with amount exactly 0.01")
        void processPayment_minimumAmount_succeeds() throws Exception {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(200L).userId(1L)
                    .amount(new BigDecimal("0.01"))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.amount").value(0.01));
        }

        @Test
        @DisplayName("should reject payment with amount of 0")
        void processPayment_zeroAmount_fails() throws Exception {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(201L).userId(1L)
                    .amount(new BigDecimal("0.00"))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Validation failed"));
        }

        @Test
        @DisplayName("should reject payment with negative amount")
        void processPayment_negativeAmount_fails() throws Exception {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(202L).userId(1L)
                    .amount(new BigDecimal("-10.00"))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Validation failed"));
        }
    }
}
