package lk.sliit.electronest.order.controller.dto;

import lk.sliit.electronest.order.model.OrderStatus;

/**
 * actorId is NOT part of this request - the actor is always the
 * authenticated caller (@AuthenticationPrincipal in the controller),
 * never a value the client sends.
 */
public record UpdateOrderStatusRequest(
        OrderStatus targetStatus,
        boolean adminOverride
) {
}
