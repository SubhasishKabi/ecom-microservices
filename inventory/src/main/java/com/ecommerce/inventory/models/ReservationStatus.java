package com.ecommerce.inventory.models;

public enum ReservationStatus {
    RESERVED,
    RELEASED,
    // Payment succeeded: the reservation converted into a permanent stock deduction (ACTION_REDUCE).
    CONFIRMED
}
