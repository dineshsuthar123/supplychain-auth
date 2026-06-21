package com.supplychain.productservice.exception;

/**
 * Thrown when the ONNX fingerprint model cannot be loaded at startup
 * or is unavailable at inference time.
 */
public class ModelNotAvailableException extends RuntimeException {

    public ModelNotAvailableException(String message) {
        super(message);
    }

    public ModelNotAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
