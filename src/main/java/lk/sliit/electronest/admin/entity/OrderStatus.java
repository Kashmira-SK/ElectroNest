package com.electronest.admin.entity;

/**
 * Order fulfilment lifecycle (see FR-OM.2 in the requirement spec).
 * PENDING -> CONFIRMED -> PROCESSING -> PACKED -> SHIPPED -> DELIVERED -> COMPLETED
 * CANCELLED can happen from most earlier states.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    PACKED,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED
}
