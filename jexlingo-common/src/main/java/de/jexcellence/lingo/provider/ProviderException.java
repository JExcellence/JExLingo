package de.jexcellence.lingo.provider;

import org.jetbrains.annotations.NotNull;

/**
 * A provider call that failed: an HTTP error, an unreadable answer or a refused request (circuit open, too many
 * requests in flight).
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class ProviderException extends RuntimeException {

    /** Status used when the request never reached the backend. */
    public static final int NOT_SENT = -1;

    private final int status;

    /**
     * Creates the exception.
     *
     * @param status  the HTTP status, or {@link #NOT_SENT}
     * @param message what went wrong, without secrets
     */
    public ProviderException(int status, @NotNull String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status, or {@link #NOT_SENT}.
     *
     * @return the HTTP status, or {@link #NOT_SENT}
     */
    public int status() {
        return status;
    }
}
