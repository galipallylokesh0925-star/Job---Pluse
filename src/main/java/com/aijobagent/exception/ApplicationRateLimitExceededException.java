package com.aijobagent.exception;

public class ApplicationRateLimitExceededException extends RuntimeException {

    public ApplicationRateLimitExceededException(String message) {
        super(message);
    }
}
