package lk.sliit.electronest.order.model;

import jakarta.persistence.*;
import lk.sliit.electronest.common.model.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order aggregate — the customer's order, its line items, and its
 * fulfilment lifecycle.
 *
 * NOTE: adjust the `customer` field's type/mapping if common.User's
 * primary key isn't a Long, or if the Role enum's values differ from
 * CUSTOMER/VENDOR/ADMIN — everything else in this module only depends
 * on this one class lining up correctly.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING_PAYMENT;

    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    private String city;

    @Column(name = "postal_code")
    private String postalCode;

    private String country;

    @Column(name = "cancellation_requested", nullable = false)
    private boolean cancellationRequested = false;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLineItem> lineItems = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public BigDecimal totalAmount() {
        return lineItems.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void addLineItem(OrderLineItem item) {
        item.setOrder(this);
        this.lineItems.add(item);
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
