package lk.sliit.electronest.order.controller.dto;

import lk.sliit.electronest.order.model.OrderStatus;
import lk.sliit.electronest.order.model.PaymentStatus;
import lk.sliit.electronest.order.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long orderId,
        Long customerId,
        OrderStatus status,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        String promoCode,
        BigDecimal discountAmount,
        boolean cancellationRequested,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        java.util.List<ItemStatus> fulfilment
) {
    public record ItemStatus(Long productId, OrderStatus status) {}

    public static OrderResponse from(Order order) { return from(order, null); }

    public static OrderResponse from(Order order, Long vendorUserId) {
        return new OrderResponse(
                order.getId(),
                order.getCustomer().getId(),
                vendorUserId == null ? order.getStatus() : order.statusForVendor(vendorUserId),
                order.getPaymentStatus(),
                vendorUserId == null ? order.totalAmount() : order.amountForVendor(vendorUserId),
                order.getPromoCode(),
                vendorUserId == null ? order.getDiscountAmount() : null,
                order.isCancellationRequested(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getLineItems().stream().filter(item -> vendorUserId == null || item.belongsToVendor(vendorUserId))
                        .map(item -> new ItemStatus(item.getProductId(), item.effectiveStatus())).toList()
        );
    }
}
