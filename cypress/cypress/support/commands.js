// Custom commands keep specs readable and avoid repeating UI steps.

/**
 * Logs in through the UI once per user and caches the session (cookies + storage),
 * so every test starts already logged in without repeating the login form.
 */
Cypress.Commands.add("login", (username = "standard_user", password = "secret_sauce") => {
  cy.session(username, () => {
    cy.visit("/");
    cy.get("[data-test=username]").type(username);
    cy.get("[data-test=password]").type(password, { log: false });
    cy.get("[data-test=login-button]").click();
    cy.url().should("include", "/inventory.html");
  });
});

/** Shorthand for the data-test selector convention used by the app. */
Cypress.Commands.add("byTest", (id) => cy.get(`[data-test="${id}"]`));
