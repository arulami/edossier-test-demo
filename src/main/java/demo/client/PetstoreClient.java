package demo.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * A small "downstream service" client, standing in for a microservice adapter in eDossier.
 * Behaviour we want to prove with tests:
 *  - 200      -> returns the pet
 *  - 404      -> Optional.empty() (not an error)
 *  - 5xx, timeout, connection reset -> retried; after maxAttempts -> PetstoreUnavailableException
 */
public class PetstoreClient {

    private final String baseUrl;
    private final Duration timeout;
    private final int maxAttempts;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public PetstoreClient(String baseUrl, Duration timeout, int maxAttempts) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = timeout;
        this.maxAttempts = maxAttempts;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public Optional<Pet> findPet(long id) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/pet/" + id))
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return Optional.of(mapper.readValue(response.body(), Pet.class));
                }
                if (status == 404) {
                    return Optional.empty();
                }
                if (status >= 500) {
                    lastFailure = new IOException("Server error " + status);
                    continue; // retry
                }
                throw new IllegalStateException("Unexpected status " + status + " for pet " + id);
            } catch (IOException e) { // includes HttpTimeoutException and connection resets
                lastFailure = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PetstoreUnavailableException("Interrupted while calling Petstore", e);
            }
        }
        throw new PetstoreUnavailableException(
                "Petstore unavailable after " + maxAttempts + " attempts", lastFailure);
    }
}
