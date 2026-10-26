package demo.client;

/** Thrown when the downstream service keeps failing after all retries. */
public class PetstoreUnavailableException extends RuntimeException {

    public PetstoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
