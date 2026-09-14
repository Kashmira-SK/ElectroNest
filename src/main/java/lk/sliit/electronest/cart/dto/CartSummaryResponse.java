package lk.sliit.electronest.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartSummaryResponse(
        List<CartItemResponse> items,
        int totalItems,
        BigDecimal subtotal
) {
}
