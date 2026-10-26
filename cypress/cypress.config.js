const { defineConfig } = require("cypress");

module.exports = defineConfig({
  e2e: {
    // Public demo shop with stable data-test attributes (good for showing selector strategy)
    baseUrl: "https://www.saucedemo.com",
    specPattern: "cypress/e2e/**/*.cy.js",
    viewportWidth: 1280,
    viewportHeight: 800,
    retries: { runMode: 1, openMode: 0 }, // one retry in CI, flakiness is still reported
    video: false,
    screenshotOnRunFailure: true,
    env: {
      petstoreUrl: "http://localhost:8080",
    },
  },
  // JUnit XML so Azure DevOps / GitHub Actions can publish results
  reporter: "junit",
  reporterOptions: {
    mochaFile: "results/cypress-[hash].xml",
    toConsole: false,
  },
});
