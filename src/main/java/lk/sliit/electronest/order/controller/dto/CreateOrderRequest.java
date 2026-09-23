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
        List<OrderLineItemRequest> items,
        String promoCode
) {
    public CreateOrderRequest(String deliveryName, String deliveryPhone, String addressLine1,
                              String addressLine2, String city, String postalCode, String country,
                              List<OrderLineItemRequest> items) {
        this(deliveryName, deliveryPhone, addressLine1, addressLine2, city, postalCode, country, items, null);
    }
}
