package com.datamate.storefront.application.exception;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("Idempotency key was already used for a different request");
    }
}
