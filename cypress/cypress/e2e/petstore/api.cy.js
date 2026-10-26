// Cypress can also drive APIs directly (useful for test-data setup before UI steps).
const api = () => `${Cypress.env("petstoreUrl")}/api/v3`;

describe("Petstore API via cy.request", () => {
  const petId = Math.floor(10_000_000 + Math.random() * 89_999_999);

  it("creates a pet and reads it back", () => {
    cy.request("POST", `${api()}/pet`, {
      id: petId,
      name: "cy-pet",
      photoUrls: [],
      status: "available",
    }).its("status").should("eq", 200);

    cy.request(`${api()}/pet/${petId}`).then((response) => {
      expect(response.status).to.eq(200);
      expect(response.body).to.include({ id: petId, name: "cy-pet", status: "available" });
    });
  });
});
