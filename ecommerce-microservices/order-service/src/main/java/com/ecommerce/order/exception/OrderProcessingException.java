package com.ecommerce.order.exception;

import org.springframework.http.HttpStatus;

public class OrderProcessingException extends RuntimeException {

    private final HttpStatus status;

    public OrderProcessingException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
