package demo.serenity;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;
import com.atlassian.oai.validator.whitelist.ValidationErrorsWhitelist;
import demo.support.TestConfig;
import io.swagger.v3.oas.models.PathItem;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.allOf;
import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.messageHasKey;
import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.methodIs;
import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.pathContains;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Serenity "step library" for the Pet API (the style of the Serenity REST tutorial).
 *
 * - Every public @Step method becomes a readable line in the Serenity report.
 * - Calls go through SerenityRest, so each request and response (headers, body, status)
 *   is recorded in the report next to the step that made it.
 * - The OpenAPI validation filter checks every call against the service's published spec,
 *   so a renamed field or an undocumented status code fails even if the functional check passes.
 */
public class PetApi {

    private static OpenApiValidationFilter openApi;

    /**
     * Known contract deviations, each documented and scoped as narrowly as possible, so that
     * everything else is still validated and a NEW deviation still fails the build.
     *
     * KNOWN-1: DELETE /pet/{petId} returns 200 "Pet deleted", but the published OpenAPI spec
     * documents no 200 response for that operation. Found by this validator; to be fixed in the
     * spec (or the service) by the provider team. Remove this rule once the spec is corrected.
     */
    private static ValidationErrorsWhitelist knownDeviations() {
        return ValidationErrorsWhitelist.create()
                .withRule("KNOWN-1: DELETE /pet/{petId} 200 not documented in the spec",
                        allOf(
                                messageHasKey("validation.response.status.unknown"),
                                methodIs(PathItem.HttpMethod.DELETE),
                                pathContains("/pet/")));
    }

    private static synchronized OpenApiValidationFilter openApi() {
        if (openApi == null) {
            openApi = new OpenApiValidationFilter(
                    OpenApiInteractionValidator
                            .createForSpecificationUrl(TestConfig.openApiSpecUrl())
                            .withWhitelist(knownDeviations())
                            .build());
        }
        return openApi;
    }

    /** Calls validated against the OpenAPI contract. */
    private RequestSpecification api() {
        return rawApi().filter(openApi());
    }

    /**
     * For negative cases the spec does not fully describe (e.g. 404 bodies).
     * No Content-Type here: GET and DELETE have no body, and the OpenAPI validator rejects a
     * Content-Type header on an operation that declares no request body. Calls that send a
     * body add it with withJsonBody().
     */
    private RequestSpecification rawApi() {
        return SerenityRest.given()
                .baseUri(TestConfig.petstoreBaseUrl())
                .accept(ContentType.JSON);
    }

    private RequestSpecification withJsonBody(Object body) {
        return api().contentType(ContentType.JSON).body(body);
    }

    private static Map<String, Object> pet(long id, String name, String status) {
        Map<String, Object> pet = new HashMap<>();
        pet.put("id", id);
        pet.put("name", name);
        pet.put("category", Map.of("id", 1, "name", "Dogs"));
        pet.put("photoUrls", List.of("https://example.org/" + name + ".jpg"));
        pet.put("status", status);
        return pet;
    }

    // ---------- actions ----------

    @Step("Register a pet named {0} with status {1}")
    public long registerPet(String name, String status) {
        long id = ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L);
        withJsonBody(pet(id, name, status)).post("/pet");
        SerenityRest.then().statusCode(200);
        return id;
    }

    @Step("Fetch pet {0}")
    public void fetchPet(long id) {
        api().get("/pet/{id}", id);
    }

    @Step("Fetch pet {0} (no contract validation)")
    public void fetchPetWithoutContractCheck(Object id) {
        rawApi().get("/pet/{id}", id);
    }

    @Step("Change the status of pet {0} named {1} to {2}")
    public void changeStatus(long id, String name, String status) {
        withJsonBody(pet(id, name, status)).put("/pet");
        SerenityRest.then().statusCode(200);
    }

    @Step("Delete pet {0}")
    public void deletePet(long id) {
        api().delete("/pet/{id}", id);
        SerenityRest.then().statusCode(200);
    }

    @Step("Search pets with status {0}")
    public void searchByStatus(String status) {
        api().queryParam("status", status).get("/pet/findByStatus");
    }

    // ---------- checks ----------

    @Step("The response status should be {0}")
    public void responseStatusShouldBe(int expected) {
        SerenityRest.then().statusCode(expected);
    }

    @Step("The pet should be named {0} with status {1}")
    public void petShouldBe(String name, String status) {
        SerenityRest.then()
                .body("name", equalTo(name))
                .body("status", equalTo(status));
    }

    @Step("The search results should include pet {0}")
    public void searchResultsShouldInclude(long id) {
        assertThat(SerenityRest.lastResponse().jsonPath().getList("id", Long.class)).contains(id);
    }

    @Step("The search results should not include pet {0}")
    public void searchResultsShouldNotInclude(long id) {
        assertThat(SerenityRest.lastResponse().jsonPath().getList("id", Long.class)).doesNotContain(id);
    }
}