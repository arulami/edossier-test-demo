describe("Login", () => {
  beforeEach(() => cy.visit("/"));

  it("lets a valid user in", () => {
    cy.byTest("username").type("standard_user");
    cy.byTest("password").type("secret_sauce", { log: false });
    cy.byTest("login-button").click();

    cy.url().should("include", "/inventory.html");
    cy.byTest("title").should("have.text", "Products");
  });

  it("blocks a locked-out user with a clear message", () => {
    cy.byTest("username").type("locked_out_user");
    cy.byTest("password").type("secret_sauce", { log: false });
    cy.byTest("login-button").click();

    cy.byTest("error")
      .should("be.visible")
      .and("contain.text", "Sorry, this user has been locked out");
    cy.url().should("not.include", "/inventory.html");
  });

  it("requires a password", () => {
    cy.byTest("username").type("standard_user");
    cy.byTest("login-button").click();
    cy.byTest("error").should("contain.text", "Password is required");
  });
});
