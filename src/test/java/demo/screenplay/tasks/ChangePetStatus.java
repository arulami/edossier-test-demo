package demo.screenplay.tasks;

import io.restassured.http.ContentType;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;
import net.serenitybdd.screenplay.rest.interactions.Get;
import net.serenitybdd.screenplay.rest.interactions.Put;

import java.util.HashMap;
import java.util.Map;

import static net.serenitybdd.screenplay.rest.questions.ResponseConsequence.seeThatResponse;

/** Reads the current pet, changes only its status, and writes it back. */
public class ChangePetStatus implements Task {

    private final String status;

    public ChangePetStatus(String status) {
        this.status = status;
    }

    public static ChangePetStatus to(String status) {
        return Tasks.instrumented(ChangePetStatus.class, status);
    }

    @Override
    @Step("{0} marks the pet as #status")
    public <T extends Actor> void performAs(T actor) {
        long id = actor.recall(RegisterPet.PET_ID);

        actor.attemptsTo(Get.resource("/pet/{id}").with(request -> request.pathParam("id", id)));
        Map<String, Object> pet = new HashMap<>(SerenityRest.lastResponse().jsonPath().getMap("$"));
        pet.put("status", status);

        actor.attemptsTo(Put.to("/pet").with(request -> request.contentType(ContentType.JSON).body(pet)));
        actor.should(seeThatResponse("the status change is accepted", response -> response.statusCode(200)));
    }
}
