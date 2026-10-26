package demo.screenplay.steps;

import demo.screenplay.questions.PetIdsWithStatus;
import demo.screenplay.tasks.ChangePetStatus;
import demo.screenplay.tasks.RegisterPet;
import demo.screenplay.tasks.SearchPets;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.rest.interactions.Get;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static net.serenitybdd.screenplay.rest.questions.ResponseConsequence.seeThatResponse;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * Step definitions only translate Gherkin into Screenplay tasks and questions.
 * All HTTP detail lives in the tasks, so these stay short and readable.
 */
public class PetLifecycleSteps {

    @When("{actor} registers a pet named {string} with status {string}")
    @Given("{actor} has registered a pet named {string} with status {string}")
    public void registersAPet(Actor actor, String name, String status) {
        actor.attemptsTo(RegisterPet.named(name, status));
    }

    @When("she/he marks the pet as {string}")
    public void marksThePetAs(String status) {
        theActorInTheSpotlight().attemptsTo(ChangePetStatus.to(status));
    }

    @When("{actor} searches for pets with status {string}")
    public void searchesForPets(Actor actor, String status) {
        actor.attemptsTo(SearchPets.withStatus(status));
    }

    @Then("the pet should be stored with name {string} and status {string}")
    public void petShouldBeStored(String name, String status) {
        Actor actor = theActorInTheSpotlight();
        long id = actor.recall(RegisterPet.PET_ID);

        actor.attemptsTo(Get.resource("/pet/{id}").with(request -> request.pathParam("id", id)));
        actor.should(seeThatResponse("the stored pet matches what was registered",
                response -> response.statusCode(200)
                        .body("name", equalTo(name))
                        .body("status", equalTo(status))));
    }

    @Then("the pet should appear in the {string} list")
    public void petShouldAppearIn(String status) {
        Actor actor = theActorInTheSpotlight();
        long id = actor.recall(RegisterPet.PET_ID);
        actor.should(seeThat(PetIdsWithStatus.of(status), hasItem(id)));
    }

    @Then("the pet should not appear in the {string} list")
    public void petShouldNotAppearIn(String status) {
        Actor actor = theActorInTheSpotlight();
        long id = actor.recall(RegisterPet.PET_ID);
        actor.should(seeThat(PetIdsWithStatus.of(status), not(hasItem(id))));
    }

    @Then("the search should be rejected with status code {int}")
    public void searchShouldBeRejected(int code) {
        theActorInTheSpotlight().should(seeThatResponse("the search is rejected",
                response -> response.statusCode(code)));
    }
}
