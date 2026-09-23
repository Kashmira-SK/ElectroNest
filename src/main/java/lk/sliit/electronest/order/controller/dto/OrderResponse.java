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
        LocalDateTime updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomer().getId(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.totalAmount(),
                order.getPromoCode(),
                order.getDiscountAmount(),
                order.isCancellationRequested(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
