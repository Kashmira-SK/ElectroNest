package lk.sliit.electronest.order.controller.dto;

import java.util.List;


public record CreateOrderRequest(
        String addressLine1,
        String city,
        String postalCode,
        String country,
        List<OrderLineItemRequest> items
) {
}
