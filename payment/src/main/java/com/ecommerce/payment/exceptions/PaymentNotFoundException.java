package com.ecommerce.payment.exceptions;

/**
 * Thrown when no payment exists for a given order. Extends IllegalArgumentException so callers that
 * treat "unknown order" as a bad argument still work; the REST layer maps it to 404.
 */
public class PaymentNotFoundException extends IllegalArgumentException {
    public PaymentNotFoundException(String message) {
        super(message);
    }
}
