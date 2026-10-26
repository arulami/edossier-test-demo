package demo.screenplay.tasks;

import io.restassured.http.ContentType;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;
import net.serenitybdd.screenplay.rest.interactions.Post;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static net.serenitybdd.screenplay.rest.questions.ResponseConsequence.seeThatResponse;

/**
 * Screenplay Task: a business-level action built from REST interactions.
 * The actor remembers the new pet's id so later steps can refer to "the pet".
 */
public class RegisterPet implements Task {

    public static final String PET_ID = "petId";
    public static final String PET_NAME = "petName";

    private final String name;
    private final String status;

    public RegisterPet(String name, String status) {
        this.name = name;
        this.status = status;
    }

    public static RegisterPet named(String name, String status) {
        return Tasks.instrumented(RegisterPet.class, name, status);
    }

    @Override
    @Step("{0} registers a pet named #name with status #status")
    public <T extends Actor> void performAs(T actor) {
        long id = ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L);

        actor.attemptsTo(
                Post.to("/pet").with(request -> request
                        .contentType(ContentType.JSON)
                        .body(Map.of("id", id, "name", name, "photoUrls", List.of(), "status", status)))
        );
        actor.should(seeThatResponse("the pet is registered", response -> response.statusCode(200)));

        actor.remember(PET_ID, id);
        actor.remember(PET_NAME, name);
    }
}
