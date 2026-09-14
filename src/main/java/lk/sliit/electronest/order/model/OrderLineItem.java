package lk.sliit.electronest.order.model;

import jakarta.persistence.*;
import lk.sliit.electronest.common.model.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One product line within an Order. unitPrice is a *snapshot* taken at
 * order time — deliberately not re-read from the catalog later, so a
 * vendor changing their price afterward never rewrites past invoices.
 *
 * NOTE: productId is stored as a plain Long rather than a JPA
 * relationship to the Catalog module's Product entity, to avoid a
 * cross-module dependency neither team has agreed on yet. Swap in a
 * proper @ManyToOne once the Catalog module's entity is finalised.
 */
@Entity
@Table(name = "order_line_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private User vendor;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    public boolean belongsToVendor(Long vendorId) {
        return this.vendor != null && this.vendor.getId().equals(vendorId);
    }
}
