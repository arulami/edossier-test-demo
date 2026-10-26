// Runs against the local Petstore container: docker compose up -d petstore
// Shows network control with cy.intercept: wait on real calls, and stub failures.
const petstore = () => Cypress.env("petstoreUrl");

describe("Petstore Swagger UI", () => {
  it("loads the API definition and renders the operations", () => {
    cy.intercept("GET", "**/api/v3/openapi.json").as("spec");
    cy.visit(petstore());

    cy.wait("@spec").its("response.statusCode").should("eq", 200);
    cy.get(".info .title").should("contain.text", "Swagger Petstore");
    cy.get(".opblock-tag").should("contain.text", "pet");
  });

  it("shows an error when the API definition cannot be loaded", () => {
    cy.intercept("GET", "**/api/v3/openapi.json", { statusCode: 500, body: "boom" }).as("spec");
    cy.visit(petstore());

    cy.wait("@spec");
    cy.contains(/failed to load api definition/i).should("be.visible");
  });
});
