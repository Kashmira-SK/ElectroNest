package com.electronest.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Minimal Order record so the Admin Dashboard can show a real order count
 * and (through Payment) real revenue instead of hardcoded zeros.
 *
 * NOTE (temporary, until modules merge): the full Order Management module
 * (items, delivery info, cancellation workflow, status audit trail) is
 * owned by Konara K.M.D.M. per the requirement spec (FR-OM.1 - FR-OM.4).
 * This class only carries the fields the Admin module needs to read for
 * reporting. When Konara's Order entity is merged into the shared database,
 * point OrderRepository at that entity instead and remove this one - the
 * table name ("orders") is kept the same on purpose so the swap is a
 * one-file change.
 */
@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime orderDate;

    @PrePersist
    protected void onCreate() {
        this.orderDate = LocalDateTime.now();
    }
}
