package com.bettingproject.operations.application.jobs;

public final class JobIdempotencyConflictException extends RuntimeException {
    public JobIdempotencyConflictException() { super("Job key has another immutable content"); }
}
