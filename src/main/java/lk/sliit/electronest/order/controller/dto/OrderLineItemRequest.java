package lk.sliit.electronest.order.controller.dto;

public record OrderLineItemRequest(
        Long productId,
        int quantity
) {
}
