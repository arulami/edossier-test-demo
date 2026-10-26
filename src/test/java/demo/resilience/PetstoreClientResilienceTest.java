package demo.resilience;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import demo.client.Pet;
import demo.client.PetstoreClient;
import demo.client.PetstoreUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service virtualisation + failure-scenario testing.
 *
 * WireMock stands in for the downstream dependency (in eDossier: a COTS component or an external
 * API that is unavailable or hard to control in test). We simulate the failures a real network
 * produces and prove our client's retry / timeout / error-handling behaviour.
 */
@DisplayName("PetstoreClient resilience against a virtualised downstream service")
class PetstoreClientResilienceTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final String PET_JSON = "{\"id\":1,\"name\":\"Rex\",\"status\":\"available\",\"photoUrls\":[]}";

    @RegisterExtension
    static WireMockExtension downstream = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private PetstoreClient client;

    @BeforeEach
    void setUp() {
        client = new PetstoreClient(downstream.baseUrl(), Duration.ofMillis(500), MAX_ATTEMPTS);
    }

    @Test
    void happyPath_returnsPet() {
        downstream.stubFor(get(urlEqualTo("/pet/1")).willReturn(okJson(PET_JSON)));

        Optional<Pet> pet = client.findPet(1);

        assertThat(pet).contains(new Pet(1, "Rex", "available"));
    }

    @Test
    void notFound_isNotAnError() {
        downstream.stubFor(get(urlEqualTo("/pet/1")).willReturn(notFound()));

        assertThat(client.findPet(1)).isEmpty();
        downstream.verify(1, getRequestedFor(urlEqualTo("/pet/1"))); // 404 must NOT be retried
    }

    @Test
    void transientServerError_isRetriedAndRecovers() {
        // Stateful stub: first call fails with 500, second call succeeds
        downstream.stubFor(get(urlEqualTo("/pet/1")).inScenario("flaky")
                .whenScenarioStateIs(STARTED)
                .willReturn(serverError())
                .willSetStateTo("recovered"));
        downstream.stubFor(get(urlEqualTo("/pet/1")).inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(okJson(PET_JSON)));

        assertThat(client.findPet(1)).isPresent();
        downstream.verify(2, getRequestedFor(urlEqualTo("/pet/1")));
    }

    @Test
    void persistentOutage_givesUpAfterMaxAttempts() {
        downstream.stubFor(get(urlEqualTo("/pet/1")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.findPet(1))
                .isInstanceOf(PetstoreUnavailableException.class)
                .hasMessageContaining(MAX_ATTEMPTS + " attempts");
        downstream.verify(MAX_ATTEMPTS, getRequestedFor(urlEqualTo("/pet/1")));
    }

    @Test
    void slowResponse_hitsTimeout() {
        downstream.stubFor(get(urlEqualTo("/pet/1"))
                .willReturn(okJson(PET_JSON).withFixedDelay(1_500))); // client timeout is 500 ms

        assertThatThrownBy(() -> client.findPet(1)).isInstanceOf(PetstoreUnavailableException.class);
    }

    @Test
    void connectionReset_isHandled() {
        downstream.stubFor(get(urlEqualTo("/pet/1"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.findPet(1)).isInstanceOf(PetstoreUnavailableException.class);
        downstream.verify(MAX_ATTEMPTS, getRequestedFor(urlEqualTo("/pet/1")));
    }

    @Test
    void garbageResponse_isHandled() {
        downstream.stubFor(get(urlEqualTo("/pet/1"))
                .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)));

        assertThatThrownBy(() -> client.findPet(1)).isInstanceOf(PetstoreUnavailableException.class);
    }
}
