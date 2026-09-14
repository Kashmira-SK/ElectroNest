package lk.sliit.electronest.order.model;

/** Mirrors the payment flag set by the Payment Processing module. */
public enum PaymentStatus {
    PENDING_PAYMENT,
    PAID,
    FAILED,
    REFUNDED
}
