package demo.screenplay.questions;

import demo.screenplay.tasks.SearchPets;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

import java.util.List;

/** Screenplay Question: "which pet ids are listed with this status?" */
public class PetIdsWithStatus implements Question<List<Long>> {

    private final String status;

    private PetIdsWithStatus(String status) {
        this.status = status;
    }

    public static PetIdsWithStatus of(String status) {
        return new PetIdsWithStatus(status);
    }

    @Override
    public List<Long> answeredBy(Actor actor) {
        actor.attemptsTo(SearchPets.withStatus(status));
        return SerenityRest.lastResponse().jsonPath().getList("id", Long.class);
    }

    @Override
    public String toString() {
        return "the ids of pets with status '" + status + "'";
    }
}
