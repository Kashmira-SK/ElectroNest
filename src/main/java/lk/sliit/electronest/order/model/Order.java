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

    @Column(name = "delivery_name", nullable = false)
    private String deliveryName;

    @Column(name = "delivery_phone", nullable = false)
    private String deliveryPhone;

    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

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


    @Column(length = 50)
    private String promoCode;

    @Column(nullable = false, columnDefinition = "numeric(38,2) default 0")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "numeric(38,2) default 0")
    private BigDecimal deliveryFee = BigDecimal.ZERO;


    public void addLineItem(OrderLineItem item) {
        item.setOrder(this);
        this.lineItems.add(item);
    }

    public boolean isMixedVendor() {
        return lineItems.stream().map(item -> item.getVendor().getId()).distinct().count() > 1;
    }

    public OrderStatus statusForVendor(Long vendorId) {
        var statuses = lineItems.stream().filter(item -> item.belongsToVendor(vendorId))
                .map(OrderLineItem::effectiveStatus).toList();
        if (statuses.isEmpty()) throw new SecurityException("You do not own items in this order");
        return aggregateStatus(statuses);
    }

    public static OrderStatus aggregateStatus(List<OrderStatus> statuses) {
        if (statuses.stream().allMatch(s -> s == OrderStatus.CANCELLED)) return OrderStatus.CANCELLED;
        if (statuses.stream().allMatch(s -> s == OrderStatus.DELIVERED)) return OrderStatus.DELIVERED;
        if (statuses.stream().anyMatch(s -> s != OrderStatus.PENDING)) return OrderStatus.PROCESSING;
        return OrderStatus.PENDING;
    }

    public boolean hasDeliveredItems() {
        return lineItems.stream().anyMatch(item -> item.effectiveStatus() == OrderStatus.DELIVERED);
    }

    /** Allocate the discounted amount consistently; the last seller receives the rounding remainder. */
    public BigDecimal amountForVendor(Long vendorId) {
        var subtotals = new java.util.TreeMap<Long, BigDecimal>();
        lineItems.forEach(item -> subtotals.merge(item.getVendor().getId(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())), BigDecimal::add));
        if (!subtotals.containsKey(vendorId)) throw new SecurityException("You do not own items in this order");
        BigDecimal subtotal = subtotalAmount(), allocated = BigDecimal.ZERO;
        for (var entry : subtotals.entrySet()) {
            BigDecimal share = entry.getKey().equals(subtotals.lastKey()) ? totalAmount().subtract(allocated)
                    : totalAmount().multiply(entry.getValue()).divide(subtotal, 2, java.math.RoundingMode.DOWN);
            if (entry.getKey().equals(vendorId)) return share;
            allocated = allocated.add(share);
        }
        throw new IllegalStateException("Seller amount unavailable");
    }

    public BigDecimal totalAmount() {
        return subtotalAmount().add(deliveryFee).subtract(discountAmount).max(BigDecimal.ZERO);
    }

    public BigDecimal subtotalAmount() {
        return lineItems.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
