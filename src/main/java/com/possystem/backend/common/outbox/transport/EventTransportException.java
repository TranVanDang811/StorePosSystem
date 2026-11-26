package com.possystem.backend.common.outbox.transport;

public class EventTransportException extends RuntimeException {
    private final boolean transientFailure;

    public EventTransportException(String message) {
        super(message);
        this.transientFailure = true;
    }

    public EventTransportException(String message, Throwable cause) {
        super(message, cause);
        this.transientFailure = true;
    }

    public EventTransportException(String message, Throwable cause, boolean transientFailure) {
        super(message, cause);
        this.transientFailure = transientFailure;
    }

    public boolean isTransientFailure() {
        return transientFailure;
    }
}
