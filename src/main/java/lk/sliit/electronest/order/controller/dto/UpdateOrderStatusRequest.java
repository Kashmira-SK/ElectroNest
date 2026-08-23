package lk.sliit.electronest.order.controller.dto;

import lk.sliit.electronest.order.model.OrderStatus;

public record UpdateOrderStatusRequest(
        Long actorId,
        OrderStatus targetStatus,
        boolean adminOverride
) {
}
