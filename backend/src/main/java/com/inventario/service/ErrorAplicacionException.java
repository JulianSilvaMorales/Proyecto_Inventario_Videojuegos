package com.inventario.service;

import org.springframework.http.HttpStatus;

public class ErrorAplicacionException extends RuntimeException {

    private final HttpStatus status;
    private final String messageKey;

    public ErrorAplicacionException(HttpStatus status, String messageKey) {
        super(messageKey);
        this.status = status;
        this.messageKey = messageKey;
    }

    public HttpStatus status() {
        return status;
    }

    public String messageKey() {
        return messageKey;
    }
}
