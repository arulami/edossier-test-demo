package demo.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import demo.client.Pet;
import demo.client.PetstoreClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer-driven contract test (consumer side).
 *
 * The consumer (our adapter, PetstoreClient) states exactly what it needs from the provider.
 * Pact starts a mock provider, runs our real client against it and writes the contract to
 * target/pacts/edossier-adapter-petstore-api.json.
 *
 * In a real pipeline that file is published to a Pact Broker; the provider team's CI replays it
 * against the real service ("provider verification") and `pact-broker can-i-deploy` gates releases.
 *
 * Difference with OpenAPI validation (see demo.serenity.PetApi): OpenAPI checks the provider against its own
 * documentation; Pact checks that the provider still satisfies what its consumers actually use.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "petstore-api")
class PetstoreClientPactTest {

    private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");

    @Pact(consumer = "edossier-adapter")
    V4Pact existingPet(PactDslWithProvider builder) {
        return builder
                .given("pet 1001 exists")
                .uponReceiving("a request for an existing pet")
                    .path("/pet/1001")
                    .method("GET")
                .willRespondWith()
                    .status(200)
                    .headers(JSON)
                    .body(new PactDslJsonBody()
                            .integerType("id", 1001)
                            .stringType("name", "Rex")
                            .stringMatcher("status", "available|pending|sold", "available"))
                .toPact(V4Pact.class);
    }

    @Pact(consumer = "edossier-adapter")
    V4Pact missingPet(PactDslWithProvider builder) {
        return builder
                .given("pet 4040 does not exist")
                .uponReceiving("a request for a pet that does not exist")
                    .path("/pet/4040")
                    .method("GET")
                .willRespondWith()
                    .status(404)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "existingPet")
    void clientReadsAnExistingPet(MockServer provider) {
        PetstoreClient client = new PetstoreClient(provider.getUrl(), Duration.ofSeconds(2), 1);

        Optional<Pet> pet = client.findPet(1001);

        assertThat(pet).isPresent();
        assertThat(pet.get().name()).isEqualTo("Rex");
        assertThat(pet.get().status()).isIn("available", "pending", "sold");
    }

    @Test
    @PactTestFor(pactMethod = "missingPet")
    void clientTreatsNotFoundAsEmpty(MockServer provider) {
        PetstoreClient client = new PetstoreClient(provider.getUrl(), Duration.ofSeconds(2), 1);

        assertThat(client.findPet(4040)).isEmpty();
    }
}
