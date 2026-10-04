package com.ecommerce.notification.exceptions;

/** The calling user could not be identified from the request (no X-User-Id header and no valid JWT). */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
