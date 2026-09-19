package com.bettingproject.operations.application.jobs;

/** A stale owner may neither acknowledge a job nor start a database mutation. */
public final class JobLeaseLostException extends RuntimeException {
    public JobLeaseLostException() { super("Job lease is no longer owned"); }
}
