package com.electronest.admin.entity;

/**
 * Transaction outcome for a payment attempt (see FR-PP.2 in the requirement spec).
 */
public enum PaymentStatus {
    SUCCESSFUL,
    FAILED,
    PENDING
}
