package demo.serenity;

import net.serenitybdd.annotations.Steps;
import net.serenitybdd.junit5.SerenityJUnit5Extension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Serenity BDD + JUnit 5 + SerenityRest, following the Serenity REST tutorial.
 * The class name starts with "When" so Failsafe picks it up during `mvn verify`.
 *
 * Each test is independent: it creates its own data, so tests can run in any order or in parallel.
 */
@ExtendWith(SerenityJUnit5Extension.class)
@DisplayName("Managing pets through the Pet API")
public class WhenManagingPets {

    @Steps
    PetApi petApi;

    @Test
    @DisplayName("A newly registered pet can be fetched by its id")
    void registeredPetCanBeFetched() {
        long id = petApi.registerPet("Kitty", "available");

        petApi.fetchPet(id);

        petApi.responseStatusShouldBe(200);
        petApi.petShouldBe("Kitty", "available");
    }

    @Test
    @DisplayName("Selling a pet moves it from the available list to the sold list")
    void sellingAPetMovesItBetweenLists() {
        long id = petApi.registerPet("Milo", "available");

        petApi.changeStatus(id, "Milo", "sold");

        petApi.searchByStatus("sold");
        petApi.searchResultsShouldInclude(id);
        petApi.searchByStatus("available");
        petApi.searchResultsShouldNotInclude(id);
    }

    @Test
    @DisplayName("A deleted pet can no longer be found")
    void deletedPetIsGone() {
        long id = petApi.registerPet("Bella", "pending");

        petApi.deletePet(id);
        petApi.fetchPetWithoutContractCheck(id);

        petApi.responseStatusShouldBe(404);
    }

    @Test
    @DisplayName("A malformed pet id is rejected")
    void malformedIdIsRejected() {
        petApi.fetchPetWithoutContractCheck("not-a-number");

        petApi.responseStatusShouldBe(400);
    }
}
