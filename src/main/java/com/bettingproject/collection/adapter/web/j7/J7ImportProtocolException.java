package com.bettingproject.collection.adapter.web.j7;

import java.util.Objects;

/** Protocol exception exposing only a bounded safe code. */
public final class J7ImportProtocolException extends IllegalArgumentException {

    private final J7ImportProtocolError error;

    J7ImportProtocolException(J7ImportProtocolError error) {
        super(Objects.requireNonNull(error, "error").name());
        this.error = error;
    }

    public J7ImportProtocolError error() {
        return error;
    }
}
