package com.ecommerce.inventory.services;


public record ReservationResult(Outcome outcome, String reason) {

    public enum Outcome {
        RESERVED,
        REJECTED,
        SKIPPED
    }

    public static ReservationResult reserved() {
        return new ReservationResult(Outcome.RESERVED, null);
    }

    public static ReservationResult rejected(String reason) {
        return new ReservationResult(Outcome.REJECTED, reason);
    }

    public static ReservationResult skipped(String reason) {
        return new ReservationResult(Outcome.SKIPPED, reason);
    }
}
