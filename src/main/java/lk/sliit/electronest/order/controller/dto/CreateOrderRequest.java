package lk.sliit.electronest.order.controller.dto;

import java.util.List;

public record CreateOrderRequest(
        String deliveryName,
        String deliveryPhone,
        String addressLine1,
        String addressLine2,
        String city,
        String postalCode,
        String country,
        List<OrderLineItemRequest> items
) {
}
