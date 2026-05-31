package io.github.abdulmalikalayande.beacon.api.exception;

import io.github.abdulmalikalayande.beacon.api.enums.ProviderName;

/**
 * Thrown when a third-party provider rejects or fails a delivery attempt.
 *
 * <p>The {@code retryable} flag tells the pipeline how to react: retryable
 * failures (timeouts, HTTP 429, HTTP 503) feed the backoff/circuit-breaker
 * machinery, while non-retryable failures (HTTP 400, malformed recipient) fail
 * fast without wasting retries.
 */
public class ProviderException extends NotificationException {

    private final ProviderName provider;
    private final int httpStatusCode;
    private final boolean retryable;

    public ProviderException(ProviderName provider, int httpStatusCode,
                             boolean retryable, String message) {
        super(message);
        this.provider = provider;
        this.httpStatusCode = httpStatusCode;
        this.retryable = retryable;
    }

    public ProviderException(ProviderName provider, int httpStatusCode,
                             boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.httpStatusCode = httpStatusCode;
        this.retryable = retryable;
    }

    /** @return the provider that produced this failure */
    public ProviderName getProvider() {
        return provider;
    }

    /** @return the provider's HTTP status code, or 0 if not applicable */
    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    /** @return whether the pipeline should treat this failure as retryable */
    public boolean isRetryable() {
        return retryable;
    }
}