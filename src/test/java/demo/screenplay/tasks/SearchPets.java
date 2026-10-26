package demo.screenplay.tasks;

import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;
import net.serenitybdd.screenplay.rest.interactions.Get;

public class SearchPets implements Task {

    private final String status;

    public SearchPets(String status) {
        this.status = status;
    }

    public static SearchPets withStatus(String status) {
        return Tasks.instrumented(SearchPets.class, status);
    }

    @Override
    @Step("{0} searches for pets with status #status")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(Get.resource("/pet/findByStatus").with(request -> request.queryParam("status", status)));
    }
}
