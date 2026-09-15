package lk.sliit.electronest.order.controller.dto;

import lk.sliit.electronest.order.model.Order;

import java.math.BigDecimal;
import java.util.List;

public record OrderViewData(
        Order order,
        List<OrderItemView> items,
        BigDecimal displayTotal
) {
}
