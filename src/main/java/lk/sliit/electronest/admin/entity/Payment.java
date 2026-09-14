package com.electronest.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Minimal Payment record so the Admin Dashboard can compute real revenue
 * (sum of SUCCESSFUL payments) instead of a hardcoded zero.
 *
 * NOTE (temporary, until modules merge): the full Payment Processing
 * module (payment methods, digital invoice/receipt, refunds) is owned by
 * Peramuna P.A.D.T. per the requirement spec (FR-PP.1 - FR-PP.4). This
 * class only carries what the Admin module needs to read for reporting.
 * When Peramuna's Payment entity is merged into the shared database, point
 * PaymentRepository at that entity instead - table name ("payments") is
 * kept the same on purpose so the swap is a one-file change.
 */
@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, unique = true, length = 50)
    private String transactionId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        this.paidAt = LocalDateTime.now();
    }
}
