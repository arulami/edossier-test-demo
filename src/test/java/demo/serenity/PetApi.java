package demo.serenity;

import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;
import demo.support.TestConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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

    private static synchronized OpenApiValidationFilter openApi() {
        if (openApi == null) {
            openApi = new OpenApiValidationFilter(TestConfig.openApiSpecUrl());
        }
        return openApi;
    }

    /** Calls validated against the OpenAPI contract. */
    private RequestSpecification api() {
        return rawApi().filter(openApi());
    }

    /** For negative cases the spec does not fully describe (e.g. 404 bodies). */
    private RequestSpecification rawApi() {
        return SerenityRest.given()
                .baseUri(TestConfig.petstoreBaseUrl())
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
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
        api().body(pet(id, name, status)).post("/pet");
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
        api().body(pet(id, name, status)).put("/pet");
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
