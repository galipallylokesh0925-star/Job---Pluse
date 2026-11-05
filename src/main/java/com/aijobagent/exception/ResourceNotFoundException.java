package com.aijobagent.exception;

/**
 * Exception thrown when a requested resource (e.g., candidate profile) is not found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
