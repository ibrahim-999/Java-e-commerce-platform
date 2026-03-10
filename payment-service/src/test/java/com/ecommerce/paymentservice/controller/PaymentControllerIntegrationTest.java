package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.BaseIntegrationTest;
import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.model.Payment;
import com.ecommerce.paymentservice.model.PaymentMethod;
import com.ecommerce.paymentservice.model.PaymentStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerIntegrationTest extends BaseIntegrationTest {

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

    // ========== Process Payment ==========

    @Nested
    @DisplayName("POST /api/payments")
    class ProcessPayment {

        @Test
        @DisplayName("should process credit card payment via Stripe and return 201")
        void processPayment_creditCard() throws Exception {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(1L).userId(1L)
                    .amount(new BigDecimal("149.99"))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.transactionId").isNotEmpty())
                    .andExpect(jsonPath("$.data.amount").value(149.99))
                    .andReturn();

            // Verify Stripe-format transaction ID (pi_...)
            String json = result.getResponse().getContentAsString();
            JsonNode node = objectMapper.readTree(json);
            String txnId = node.get("data").get("transactionId").asText();
            assertThat(txnId).startsWith("pi_");

            // Verify transaction history was created
            assertThat(transactionRepository.count()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should process PayPal payment with PAY- prefix")
        void processPayment_paypal() throws Exception {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(2L).userId(1L)
                    .amount(new BigDecimal("50.00"))
                    .paymentMethod("PAYPAL")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            JsonNode node = objectMapper.readTree(json);
            String txnId = node.get("data").get("transactionId").asText();
            assertThat(txnId).startsWith("PAY-");
        }

        @Test
        @DisplayName("should reject duplicate payment for same order")
        void processPayment_duplicate() throws Exception {
            paymentRepository.save(Payment.builder()
                    .orderId(1L).userId(1L)
                    .amount(new BigDecimal("100.00"))
                    .status(PaymentStatus.COMPLETED)
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .transactionId("pi_existing123")
                    .build());

            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(1L).userId(1L)
                    .amount(new BigDecimal("100.00"))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("completed payment already exists")));
        }

        @Test
        @DisplayName("should return 400 for missing required fields")
        void processPayment_validation() throws Exception {
            CreatePaymentRequest request = new CreatePaymentRequest();

            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Validation failed"));
        }

        @Test
        @DisplayName("should return 400 for invalid payment method")
        void processPayment_invalidMethod() throws Exception {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(3L).userId(1L)
                    .amount(new BigDecimal("50.00"))
                    .paymentMethod("BITCOIN")
                    .build();

            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Invalid payment method")));
        }
    }

    // ========== Get Payment ==========

    @Nested
    @DisplayName("GET /api/payments")
    class GetPayment {

        @Test
        @DisplayName("should get payment by ID")
        void getById() throws Exception {
            Payment saved = paymentRepository.save(Payment.builder()
                    .orderId(1L).userId(1L)
                    .amount(new BigDecimal("75.00"))
                    .status(PaymentStatus.COMPLETED)
                    .paymentMethod(PaymentMethod.PAYPAL)
                    .transactionId("PAY-test123")
                    .build());

            mockMvc.perform(get("/api/payments/" + saved.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderId").value(1))
                    .andExpect(jsonPath("$.data.paymentMethod").value("PAYPAL"))
                    .andExpect(jsonPath("$.data.transactionId").value("PAY-test123"));
        }

        @Test
        @DisplayName("should return 404 for non-existent payment")
        void getById_notFound() throws Exception {
            mockMvc.perform(get("/api/payments/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("should get payment by order ID")
        void getByOrderId() throws Exception {
            paymentRepository.save(Payment.builder()
                    .orderId(42L).userId(1L)
                    .amount(new BigDecimal("200.00"))
                    .status(PaymentStatus.COMPLETED)
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .transactionId("ACH-order42")
                    .build());

            mockMvc.perform(get("/api/payments/order/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderId").value(42))
                    .andExpect(jsonPath("$.data.paymentMethod").value("BANK_TRANSFER"));
        }

        @Test
        @DisplayName("should get all payments for a user")
        void getByUserId() throws Exception {
            paymentRepository.save(Payment.builder()
                    .orderId(1L).userId(5L)
                    .amount(new BigDecimal("50.00"))
                    .status(PaymentStatus.COMPLETED)
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .transactionId("pi_u5a")
                    .build());
            paymentRepository.save(Payment.builder()
                    .orderId(2L).userId(5L)
                    .amount(new BigDecimal("75.00"))
                    .status(PaymentStatus.COMPLETED)
                    .paymentMethod(PaymentMethod.DEBIT_CARD)
                    .transactionId("pi_u5b")
                    .build());

            mockMvc.perform(get("/api/payments/user/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }
    }

    // ========== Transaction History ==========

    @Nested
    @DisplayName("GET /api/payments/{id}/transactions")
    class TransactionHistory {

        @Test
        @DisplayName("should return transaction history after payment processing")
        void getTransactionHistory() throws Exception {
            // Process a payment — this creates transaction history entries
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(10L).userId(1L)
                    .amount(new BigDecimal("99.99"))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            MvcResult createResult = mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Get the payment ID from the create response
            JsonNode createNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
            Long paymentId = createNode.get("data").get("id").asLong();

            // Now get the transaction history
            mockMvc.perform(get("/api/payments/" + paymentId + "/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.data[0].type").value("CHARGE"))
                    .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data[0].gatewayName").value("STRIPE"));
        }
    }

    // ========== Refund ==========

    @Nested
    @DisplayName("PUT /api/payments/{id}/refund")
    class Refund {

        @Test
        @DisplayName("should refund a completed payment and log the refund transaction")
        void refund_success() throws Exception {
            // First create a payment
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(20L).userId(1L)
                    .amount(new BigDecimal("99.99"))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            MvcResult createResult = mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode createNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
            Long paymentId = createNode.get("data").get("id").asLong();

            // Now refund it
            mockMvc.perform(put("/api/payments/" + paymentId + "/refund"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                    .andExpect(jsonPath("$.message").value("Payment refunded successfully"));

            // Verify refund transaction was logged
            mockMvc.perform(get("/api/payments/" + paymentId + "/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        }

        @Test
        @DisplayName("should reject refund of already-refunded payment")
        void refund_alreadyRefunded() throws Exception {
            Payment saved = paymentRepository.save(Payment.builder()
                    .orderId(1L).userId(1L)
                    .amount(new BigDecimal("99.99"))
                    .status(PaymentStatus.REFUNDED)
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .transactionId("pi_alreadydone")
                    .build());

            mockMvc.perform(put("/api/payments/" + saved.getId() + "/refund"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("already been refunded")));
        }

        @Test
        @DisplayName("should reject refund of pending payment")
        void refund_pendingPayment() throws Exception {
            Payment saved = paymentRepository.save(Payment.builder()
                    .orderId(1L).userId(1L)
                    .amount(new BigDecimal("99.99"))
                    .status(PaymentStatus.PENDING)
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .build());

            mockMvc.perform(put("/api/payments/" + saved.getId() + "/refund"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Only completed payments")));
        }
    }
}
