package demo.screenplay.steps;

import demo.support.TestConfig;
import io.cucumber.java.Before;
import io.cucumber.java.ParameterType;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.actors.Cast;
import net.serenitybdd.screenplay.actors.OnStage;
import net.serenitybdd.screenplay.rest.abilities.CallAnApi;

/**
 * Before each scenario, set the stage: every actor gets the ability to call the Petstore API.
 * The {actor} parameter type turns a capitalised name in Gherkin ("Clara") into a Screenplay actor.
 */
public class StageSetup {

    @Before
    public void setTheStage() {
        OnStage.setTheStage(Cast.whereEveryoneCan(CallAnApi.at(TestConfig.petstoreBaseUrl())));
    }

    @ParameterType("[A-Z][a-z]+")
    public Actor actor(String name) {
        return OnStage.theActorCalled(name);
    }
}
