describe("Checkout (end-to-end user flow)", () => {
  beforeEach(() => {
    cy.login(); // custom command with cy.session caching
    cy.visit("/inventory.html", { failOnStatusCode: false });
  });

  it("buys one product from catalogue to confirmation", () => {
    cy.byTest("add-to-cart-sauce-labs-backpack").click();
    cy.byTest("shopping-cart-badge").should("have.text", "1");

    cy.byTest("shopping-cart-link").click();
    cy.byTest("inventory-item-name").should("have.text", "Sauce Labs Backpack");
    cy.byTest("checkout").click();

    cy.fixture("checkout").then((customer) => {
      cy.byTest("firstName").type(customer.firstName);
      cy.byTest("lastName").type(customer.lastName);
      cy.byTest("postalCode").type(customer.postalCode);
    });
    cy.byTest("continue").click();

    // Data integrity on the overview page: item total + tax = total
    cy.byTest("subtotal-label").invoke("text").then((subtotalText) => {
      cy.byTest("tax-label").invoke("text").then((taxText) => {
        cy.byTest("total-label").invoke("text").then((totalText) => {
          const amount = (t) => Number(t.replace(/[^0-9.]/g, ""));
          expect(amount(subtotalText) + amount(taxText)).to.be.closeTo(amount(totalText), 0.01);
        });
      });
    });

    cy.byTest("finish").click();
    cy.byTest("complete-header").should("have.text", "Thank you for your order!");
  });
});
