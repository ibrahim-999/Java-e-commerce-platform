package com.ecommerce.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

// Simulates a bank transfer (ACH/wire) gateway.
//
// In production, this might use Plaid, Dwolla, or a direct bank API.
// Bank transfers are cheaper (~0.5%) but slower (1-3 business days to settle).
//
// Key difference from cards: bank transfers are NOT instant.
// In a real system, this would return PENDING and settle asynchronously
// via a webhook callback days later.

@Component
@Slf4j
public class BankTransferGateway implements PaymentGateway {

    @Override
    public GatewayResponse charge(Long orderId, BigDecimal amount) {
        log.info("[BankTransfer] Initiating transfer for order {}, amount: ${}", orderId, amount);

        // Simulate ACH/wire transfer initiation
        String transactionId = "ACH-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase();

        log.info("[BankTransfer] Transfer initiated: {} (settles in 1-3 business days)", transactionId);
        return GatewayResponse.success(transactionId);
    }

    @Override
    public GatewayResponse refund(String transactionId, BigDecimal amount) {
        log.info("[BankTransfer] Refunding {} for transaction: {}", amount, transactionId);

        String refundId = "ACHREF-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();

        log.info("[BankTransfer] Refund initiated: {}", refundId);
        return GatewayResponse.success(refundId);
    }

    @Override
    public String gatewayName() {
        return "BANK_TRANSFER";
    }
}
