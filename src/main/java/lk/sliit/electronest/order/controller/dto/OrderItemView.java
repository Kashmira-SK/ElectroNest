package lk.sliit.electronest.order.controller.dto;

import java.math.BigDecimal;

public record OrderItemView(
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
