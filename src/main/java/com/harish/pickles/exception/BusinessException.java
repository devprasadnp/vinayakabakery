package com.harish.pickles.exception;

/** Anything the customer did that we can explain back to them (out of stock, empty cart...). */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
