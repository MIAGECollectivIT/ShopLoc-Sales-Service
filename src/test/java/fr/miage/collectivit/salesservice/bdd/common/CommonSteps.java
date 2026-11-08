package fr.miage.collectivit.salesservice.bdd.common;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.spring.CucumberContextConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest
@RequiredArgsConstructor
public class CommonSteps extends AbstractScenario {

  private final ScenarioContext scenarioContext;

  @Given("an authenticated user with role {string}")
  public void givenAuthenticatedUserWithRole(String role) {
    scenarioContext.setJwtToken(generateJwtToken(role));
  }

  @Given("an unauthenticated user")
  public void givenUnauthenticatedUser() {
    scenarioContext.setJwtToken(null);
  }

  @Then("the HTTP response status should be {int}")
  public void thenHttpResponseStatusShouldBe(int expectedStatus) throws Exception {
    scenarioContext.getLastResult().andExpect(status().is(expectedStatus));
  }

  @And("the response JSON field {string} should equal {string}")
  public void andResponseJsonFieldShouldEqual(String fieldPath, String expectedValue)
      throws Exception {
    scenarioContext.getLastResult().andExpect(jsonPath("$." + fieldPath).value(expectedValue));
  }

  @And("the response JSON field {string} should not be empty")
  public void andResponseJsonFieldShouldNotBeEmpty(String fieldPath) throws Exception {
    scenarioContext.getLastResult().andExpect(jsonPath("$." + fieldPath).isNotEmpty());
  }
}
