# eDossier test automation demo

A runnable reference setup for testing an OpenAPI-based microservice landscape, from fast contract and resilience checks up to UI flows, load tests and live dashboards.

**Java 21 + Maven · JUnit 5 · Serenity BDD 5 (SerenityRest, Screenplay, Cucumber) · OpenAPI contract validation · Pact · WireMock · SQL integrity checks · Bruno · Cypress · k6 · InfluxDB + Grafana · Azure DevOps and GitHub Actions**

The system under test is the **Swagger Petstore v3** API, run locally in Docker. It is a real OpenAPI 3 REST service, so the same techniques apply to any microservice that publishes an OpenAPI contract.

---

## 1. What's in the repo

| Folder / file | Tool | What it covers |
| --- | --- | --- |
| `src/test/java/demo/serenity/` | Serenity BDD + JUnit 5 + SerenityRest | REST tests built on a `@Steps` library, following the [Serenity REST tutorial](https://serenity-bdd.github.io/docs/tutorials/rest). Every step is a readable line in the report with its HTTP request and response attached, and every call is validated against the live OpenAPI document. |
| `src/test/resources/features/` + `src/test/java/demo/screenplay/` | Serenity Screenplay + Cucumber | Business-readable Gherkin driven by Screenplay actors (`Clara`), Tasks (`RegisterPet`), Questions (`PetIdsWithStatus`) and REST interactions (`Post.to`, `Get.resource`) |
| `target/site/serenity/index.html` (generated) | Serenity reports | Living documentation: results per feature, step-level detail, full REST request/response per step |
| `src/test/java/demo/contract/` | Pact JVM | Consumer-driven contract tests; write `target/pacts/*.json` for provider verification |
| `src/test/java/demo/resilience/` | WireMock | Service virtualisation and fault injection: retry on 500, give up after 3 attempts, timeouts, connection resets, garbage responses |
| `src/test/java/demo/db/` | JDBC + H2 | SQL data-integrity checks: orphan records, duplicates, invalid statuses, time-order violations |
| `src/main/java/demo/client/` | Java `HttpClient` | A small adapter with timeout and retry, the production code exercised by the contract and resilience tests |
| `bruno/petstore/` | Bruno | Git-friendly API collection with assertions and JavaScript tests; runs headless in CI |
| `cypress/` | Cypress | UI end-to-end: login, full checkout, `data-test` selectors, `cy.session`, custom commands, `cy.intercept` (wait on a call and stub a failure), `cy.request` |
| `k6/petstore-load.js` | k6 | Five load profiles (smoke, load, stress, spike, soak), two traffic models, per-endpoint thresholds as a CI quality gate, custom business metrics, JUnit output |
| `docker-compose.yml`, `infra/grafana/` | InfluxDB + Grafana | Provisioned dashboard with live k6 metrics: VUs, throughput, p95 per endpoint, error rate, check pass rate, business metrics |
| `azure-pipelines.yml` | Azure DevOps | Staged pipeline: fast tests → Serenity/Bruno → Cypress and k6, publishing JUnit results and reports |
| `.github/workflows/ci.yml` | GitHub Actions | The same pipeline on GitHub |

### Test pyramid

```
                 ▲  few, slow, costly
        Cypress E2E (UI flows)                           cypress/
     Serenity REST + Screenplay/Cucumber, Bruno (live)    mvn verify, bru run
   Pact contracts · WireMock faults · SQL checks         mvn test   (no server, seconds)
                 ▼  many, fast, cheap
   + k6 performance gate and Grafana monitoring alongside
```

---

## 2. Prerequisites

| Tool | Version | Check with |
| --- | --- | --- |
| JDK | 21 (Temurin recommended) | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js | 20 LTS | `node -v` |
| Docker Desktop | recent | `docker compose version` |
| Git | any | `git --version` |
| k6 (optional; Docker works too) | latest (`brew install k6`, `winget install k6` or `choco install k6`) | `k6 version` |
| Bruno desktop app (optional) | latest, from usebruno.com | |

Commands below work in macOS/Linux shells, PowerShell and Git Bash.

```bash
git clone https://github.com/<user>/edossier-test-demo.git
cd edossier-test-demo
```

---

## 3. Running the layers

### 3.1 Fast tests (no server needed)

```bash
mvn test
```

This runs the Pact, WireMock and SQL tests with Surefire: 14 tests in a few seconds.

| Output | Where |
| --- | --- |
| JUnit reports | `target/surefire-reports/` |
| Pact contract | `target/pacts/edossier-adapter-petstore-api.json` |

**Provider verification** is the other half of contract testing, and the next step in a real pipeline:

1. The Pact file is published to a Pact Broker.
2. The provider's CI replays each interaction against the real service, using the provider states (`pet 1001 exists`) to set up data.
3. It publishes the verification result.
4. `can-i-deploy` blocks any release that would break a consumer.

### 3.2 Local environment

```bash
docker compose up -d
docker compose ps
```

| Service | URL |
| --- | --- |
| Petstore Swagger UI | http://localhost:8080 |
| Petstore API | http://localhost:8080/api/v3 |
| OpenAPI document | http://localhost:8080/api/v3/openapi.json |
| Grafana (no login, opens on the k6 dashboard) | http://localhost:3000 |
| InfluxDB (k6 metrics store) | http://localhost:8086 |

### 3.3 Serenity BDD REST tests and the living-documentation report

```bash
mvn clean verify
```

Then open `target/site/serenity/index.html`.

**What `verify` does:**

1. **Surefire** runs the fast tests.
2. **Failsafe** runs the Serenity tests against the live API. It picks up classes named `When*` and the `CucumberTestSuite`.
3. **`serenity:aggregate`** builds the HTML report, even when tests fail (`testFailureIgnore`).
4. **`serenity:check`** then fails the build if any Serenity test failed. The result is always a full report *and* a correct red/green build.

**a) JUnit 5 + step library** (`demo/serenity/`):

```java
@ExtendWith(SerenityJUnit5Extension.class)
public class WhenManagingPets {
    @Steps PetApi petApi;

    @Test void registeredPetCanBeFetched() {
        long id = petApi.registerPet("Kitty", "available");
        petApi.fetchPet(id);
        petApi.responseStatusShouldBe(200);
        petApi.petShouldBe("Kitty", "available");
    }
}
```

- Each `@Step` method in `PetApi` becomes a report line.
- Calls go through `SerenityRest`, so the report shows the exact request and response under each step.
- The `OpenApiValidationFilter` validates every call against the published spec. A renamed field, a wrong type or an undocumented status code fails the test even when the functional assertion passes.

**b) Screenplay + Cucumber** (`demo/screenplay/` + `features/pets/pet_lifecycle.feature`):

```gherkin
Given Clara has registered a pet named "Milo" with status "available"
When she marks the pet as "sold"
Then the pet should appear in the "sold" list
```

| Screenplay concept | In this repo |
| --- | --- |
| **Actor** | Clara, a clerk, created by the `{actor}` parameter type |
| **Ability** | `CallAnApi.at(baseUrl)`, given to every actor in `StageSetup` |
| **Tasks** | `RegisterPet`, `ChangePetStatus`, `SearchPets`: business actions built from `Post.to` / `Get.resource` / `Put.to` |
| **Questions** | `PetIdsWithStatus`, checked with `seeThat(..., hasItem(id))`, and `seeThatResponse(...)` for response checks |
| **Memory** | `actor.remember("petId", id)` / `actor.recall(...)`, so steps share state without static variables |

**Design notes:**

- **Screenplay scales better than page or step objects.** Tasks compose, actors can hold several abilities (API, browser, database), and the code reads like the business process.
- **Living documentation.** The report is organised by feature (folders under `features/`), so non-technical stakeholders can see what is covered and what passed.
- **One reporting layer.** REST, UI and Cucumber tests all report to the same place.
- **Independent tests.** Each test creates its own data, so tests can run in any order or in parallel.

Run against another environment without changing code:

```bash
mvn verify -Dpetstore.baseUrl=https://petstore3.swagger.io/api/v3
```

### 3.4 Bruno

**GUI:** open the Bruno app, choose *Open Collection*, select `bruno/petstore`, pick the `local` environment, then run the collection (*Runner*).

**CLI, as used in CI:**

```bash
cd bruno/petstore
npx @usebruno/cli run --env local
npx @usebruno/cli run --env local --reporter-junit ../../bruno-results.xml
```

- Each request is a plain-text `.bru` file, so changes are versioned in Git and reviewed in pull requests.
- It works offline, with no account.
- `assert` blocks give declarative checks; `tests` blocks hold JavaScript (Chai) for logic.
- Environments switch between dev, test and acceptance without editing requests.

### 3.5 Cypress

```bash
cd cypress
npm install
npx cypress open                     # interactive runner
npx cypress run                      # headless, JUnit XML in cypress/results/
npm run cy:run:ui                    # saucedemo UI specs only (needs internet)
npm run cy:run:petstore              # local Petstore specs only (needs docker compose up)
```

| Spec | Covers |
| --- | --- |
| `saucedemo/login.cy.js` | positive and negative login, error messages, `data-test` selectors |
| `saucedemo/checkout.cy.js` | full purchase flow: `cy.session` login caching, fixtures, a data-integrity check (subtotal + tax = total) |
| `petstore/swagger-ui.cy.js` | `cy.intercept`: waiting on a real network call, then stubbing a 500 to test the error state |
| `petstore/api.cy.js` | `cy.request` for API calls and test-data setup |

Selectors use dedicated `data-test` attributes rather than CSS classes or XPath. Assertions retry automatically, so there are no fixed sleeps.

### 3.6 k6 load tests with live Grafana metrics

**Through Docker Compose** (no install; metrics go to InfluxDB and Grafana automatically):

```bash
docker compose run --rm k6 run /scripts/petstore-load.js                    # "load" profile
docker compose run --rm k6 run -e PROFILE=smoke  /scripts/petstore-load.js
docker compose run --rm k6 run -e PROFILE=stress /scripts/petstore-load.js
```

**With k6 installed locally:**

```bash
cd k6
k6 run -o influxdb=http://localhost:8086/k6 petstore-load.js
k6 run -o influxdb=http://localhost:8086/k6 -e PROFILE=spike petstore-load.js
```

While a test runs, open http://localhost:3000. The **"k6 – Petstore load test"** dashboard opens by default and refreshes every 5 seconds:

| Panel | What it shows |
| --- | --- |
| Total requests · Error rate · p95 · Peak VUs | Headline numbers, coloured against the thresholds |
| Virtual users and request rate | Saturation: throughput stops growing while VUs keep rising |
| p95 response time per endpoint | Which endpoint degrades first (requests are tagged by `endpoint`) |
| Error rate | Must stay under 1% |
| Check pass rate per check | Functional correctness under load, not just speed |
| Response time percentiles | p50 vs p95 vs p99; a widening gap points to queueing or outliers |
| Business flow: time to sell | Custom k6 metrics (`Trend`, `Counter`) for what the business cares about |

Each run ends with a console summary plus `k6/results/k6-summary.json` and `k6/results/k6-junit.xml`. If a threshold fails, k6 exits with a non-zero code, which fails the pipeline stage.

**Script design:**

- **Two traffic models.**
  - `browsers` uses `ramping-vus`, a closed model: a fixed number of users, each waiting for its response.
  - `clerks` uses `constant-arrival-rate`, an open model: requests keep arriving even when the system slows down. This matches public-facing traffic and exposes queueing that closed models hide.
- **Profiles from one script** (`-e PROFILE=...`):

  | Profile | Purpose |
  | --- | --- |
  | `smoke` | CI sanity check |
  | `load` | Expected peak |
  | `stress` | Find the breaking point and check recovery |
  | `spike` | Sudden burst, e.g. a deadline day |
  | `soak` | One hour at moderate load: memory leaks, connection-pool exhaustion |

- **Thresholds as SLAs:**
  - overall p95 < 800 ms and max < 3 s
  - per-endpoint p95 (`findByStatus` < 500 ms, `createPet` < 1 s)
  - errors < 1%
  - checks > 99%
- **Low metric cardinality.** Requests are named by endpoint rather than full URL (pet ids differ per call), which keeps InfluxDB and Grafana fast.
- **Functional checks under load.** For example, "createPet echoes the id" catches responses that are fast but wrong.

**In a production-like setup:**

- Derive the workload model from production analytics or business estimates, and agree SLAs with the business before testing.
- Run against a production-like environment with the load generator on separate machines (or Grafana Cloud k6). Numbers from a laptop measure the laptop as much as the application.
- Put service metrics (CPU, memory, DB connections, GC) from Prometheus and logs from Loki next to the k6 data in Grafana, so a slow p95 can be traced to its cause.
- k6 can also write to Prometheus via remote-write (`-o experimental-prometheus-rw`) for a Prometheus-based Grafana stack.

### 3.7 Azure DevOps pipeline

1. In Azure DevOps, create a project.
2. Choose **Pipelines → New pipeline → GitHub**, select the repo, then **Existing Azure Pipelines YAML file** and pick `/azure-pipelines.yml`.
3. Run it. Results appear in the run's **Tests** tab; the Serenity report, Pact contract and k6 results are published as **artifacts**.

New organisations may need a free *parallel jobs grant* before hosted agents run. The GitHub Actions workflow runs the same stages in the meantime.

**Pipeline design:**

- Fast tests gate the slow ones.
- UI and performance stages run in parallel after the API stage.
- Every stage publishes JUnit XML.
- The test environment starts inside the job (`docker compose`).
- Maven dependencies are cached.
- The performance stage runs only the **smoke** profile; full load tests belong on a schedule or before a release.
- Natural next steps: a Pact Broker publish and verification step, and environment approvals (dev → test → acc).

### Stop everything

```bash
docker compose down        # add -v to also delete the stored k6 metrics
```

---

## 4. Troubleshooting

| Problem | Fix |
| --- | --- |
| A Maven dependency version can't be resolved | Update the version in `pom.xml` `<properties>` to the latest on search.maven.org |
| Cucumber errors such as `NoSuchMethodError` / plugin not found | Align `cucumber.version` with the version Serenity brings in: `mvn dependency:tree -Dincludes=io.cucumber` |
| `SerenityReporterParallel` class not found | In Serenity 5 the plugin is `net.serenitybdd.cucumber.core.plugin.SerenityReporterParallel` (it was `io.cucumber.core.plugin...` in Serenity 4). Match it to the Serenity version in `CucumberTestSuite`. |
| `serenity:check` fails the build | A Serenity test failed. Open `target/site/serenity/index.html` for the failing step and its REST details. |
| A Petstore response fails OpenAPI validation | The demo service occasionally drifts from its own spec. Treat it as a real contract finding. |
| `The requested image's platform (linux/amd64) does not match the detected host platform (linux/arm64/v8)` (Apple Silicon) | The Petstore image is Intel/AMD-only; `docker-compose.yml` already sets `platform: linux/amd64`. In Docker Desktop → Settings → General, enable **Use Rosetta for x86_64/amd64 emulation on Apple Silicon**. The other images run natively on ARM. |
| Port 8080, 3000 or 8086 already in use | Change the left-hand port in `docker-compose.yml` and pass `-Dpetstore.baseUrl=...` / `-e BASE_URL=...` |
| Grafana dashboard is empty | Make sure k6 ran with the InfluxDB output (the Docker Compose route does this automatically), and widen the time range |
| k6 in Docker cannot write `results/` (Linux hosts) | `chmod 777 k6/results` |
| k6 cannot load `jslib.k6.io` (offline / proxy) | Remove the `handleSummary` function; k6 then prints its default summary |
| Saucedemo selectors changed | Inspect the page and update the `data-test` value |

---

## 5. Project layout

```
edossier-test-demo/
├── pom.xml                          Serenity 5, Surefire (fast) + Failsafe (Serenity) + serenity-maven-plugin
├── docker-compose.yml               petstore + influxdb + grafana (+ k6 runner on demand)
├── azure-pipelines.yml              Azure DevOps pipeline
├── .github/workflows/ci.yml         GitHub Actions pipeline
├── infra/grafana/                   datasource + k6 dashboard (provisioned automatically)
├── k6/petstore-load.js              k6 script: profiles, scenarios, thresholds, custom metrics
├── bruno/petstore/                  Bruno collection + local environment
├── cypress/                         Cypress project (config, specs, commands, fixtures)
└── src/
    ├── main/java/demo/client/       PetstoreClient (adapter with timeout + retry)
    └── test/
        ├── java/demo/serenity/      Serenity JUnit 5 tests + PetApi step library (SerenityRest + OpenAPI validation)
        ├── java/demo/screenplay/    CucumberTestSuite, steps, Screenplay tasks and questions
        ├── java/demo/contract/      Pact consumer tests
        ├── java/demo/resilience/    WireMock fault-injection tests
        ├── java/demo/db/            SQL data-integrity tests (H2)
        └── resources/               features/pets/*.feature, serenity.conf, junit-platform.properties
```
