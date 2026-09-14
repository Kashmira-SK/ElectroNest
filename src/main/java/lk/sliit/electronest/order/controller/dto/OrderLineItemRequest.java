package lk.sliit.electronest.order.controller.dto;

import java.math.BigDecimal;

/**
 * NOTE: unitPrice is accepted from the client for now, since there's no
 * shared way yet to call into the Catalog module for the authoritative
 * price. TODO once Catalog exposes a lookup: re-fetch/verify the price
 * server-side here instead of trusting the client's number, otherwise
 * a customer could submit an arbitrarily low price.
 */
public record OrderLineItemRequest(
        Long productId,
        Long vendorId,
        int quantity,
        BigDecimal unitPrice
) {
}
