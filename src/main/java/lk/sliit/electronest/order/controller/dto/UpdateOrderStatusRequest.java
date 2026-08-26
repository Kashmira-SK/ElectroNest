package lk.sliit.electronest.order.controller.dto;

import lk.sliit.electronest.order.model.OrderStatus;

public record UpdateOrderStatusRequest(
        OrderStatus targetStatus,
        boolean adminOverride
) {
}
