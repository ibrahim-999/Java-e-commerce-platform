package com.ecommerce.paymentservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// PaymentTransaction — an audit log entry for every payment attempt.
//
// While the Payment entity tracks the CURRENT state of a payment,
// PaymentTransaction tracks EVERY attempt, including failures.
//
// Example timeline for a payment that fails twice then succeeds:
//   Transaction 1: CHARGE_ATTEMPT → FAILED  ("Card declined")
//   Transaction 2: CHARGE_ATTEMPT → FAILED  ("Insufficient funds")
//   Transaction 3: CHARGE_ATTEMPT → SUCCESS ("pi_3abc...")
//
// Why do we need this?
//   1. Debugging: "Why did this payment fail?" — look at the transaction log
//   2. Dispute resolution: Customer says "I was charged twice" — check the log
//   3. Compliance: Financial regulations (PCI-DSS, SOX) require audit trails
//   4. Analytics: "What's our card decline rate?" — query transaction history

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Links back to the parent Payment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // What type of operation was attempted
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    // Did this attempt succeed or fail?
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    // Which gateway processed this attempt
    @Column(nullable = false)
    private String gatewayName;

    // The transaction ID from the gateway (null if failed)
    private String gatewayTransactionId;

    // Amount involved in this transaction
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // Human-readable message (success confirmation or error reason)
    private String message;

    // Which attempt number this is (1st try, 2nd retry, etc.)
    @Column(nullable = false)
    private int attemptNumber;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
