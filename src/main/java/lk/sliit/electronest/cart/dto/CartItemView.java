package lk.sliit.electronest.cart.dto;

import java.math.BigDecimal;

public record CartItemView(
        Long itemId,
        Long productId,
        String name,
        String brand,
        String imageUrl,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        Integer stockQuantity
) {
}
