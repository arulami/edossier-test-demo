// k6 load test for the Petstore API.
//
// Run (k6 installed locally):
//   k6 run k6/petstore-load.js                                  -> "load" profile, no dashboard
//   k6 run -o influxdb=http://localhost:8086/k6 k6/petstore-load.js   -> live metrics in Grafana
//   k6 run -e PROFILE=stress k6/petstore-load.js                 -> other profiles: smoke, load, stress, spike, soak
//
// Run (no install, through Docker Compose - writes to InfluxDB/Grafana automatically):
//   docker compose run --rm k6 run /scripts/petstore-load.js
//   docker compose run --rm k6 run -e PROFILE=smoke /scripts/petstore-load.js

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";
import { textSummary, jUnit } from "https://jslib.k6.io/k6-summary/0.0.2/index.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api/v3";
const PROFILE = __ENV.PROFILE || "load";

// ---------- workload profiles: same script, different shape of traffic ----------
// Read traffic ("browsers") is a ramping number of virtual users (closed model).
// Write traffic ("clerks") is a fixed arrival rate (open model): new requests keep arriving
// even if the system slows down, which is how real public traffic behaves.
const profiles = {
  smoke: { // CI sanity check: does it work at all under minimal load?
    browsers: [{ duration: "10s", target: 2 }, { duration: "20s", target: 2 }],
    clerkRate: 1, clerkDuration: "30s",
  },
  load: { // expected normal peak
    browsers: [{ duration: "30s", target: 30 }, { duration: "1m", target: 30 }, { duration: "15s", target: 0 }],
    clerkRate: 2, clerkDuration: "1m30s",
  },
  stress: { // beyond normal peak: where does it break, and does it recover?
    browsers: [{ duration: "1m", target: 100 }, { duration: "2m", target: 200 }, { duration: "1m", target: 0 }],
    clerkRate: 10, clerkDuration: "4m",
  },
  spike: { // sudden burst, e.g. a deadline day
    browsers: [{ duration: "10s", target: 5 }, { duration: "10s", target: 300 }, { duration: "1m", target: 300 }, { duration: "20s", target: 5 }],
    clerkRate: 5, clerkDuration: "1m40s",
  },
  soak: { // long run at moderate load: memory leaks, connection exhaustion, log growth
    browsers: [{ duration: "2m", target: 20 }, { duration: "1h", target: 20 }, { duration: "2m", target: 0 }],
    clerkRate: 1, clerkDuration: "1h4m",
  },
};
const p = profiles[PROFILE];
if (!p) {
  throw new Error(`Unknown PROFILE "${PROFILE}". Use one of: ${Object.keys(profiles).join(", ")}`);
}

export const options = {
  scenarios: {
    browsers: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: p.browsers,
      exec: "browse",
    },
    clerks: {
      executor: "constant-arrival-rate",
      rate: p.clerkRate,
      timeUnit: "1s",
      duration: p.clerkDuration,
      preAllocatedVUs: 10,
      maxVUs: 100,
      startTime: "5s",
      exec: "registerAndSell",
    },
  },

  // Pass/fail criteria: k6 exits with a non-zero code when a threshold fails -> CI quality gate.
  thresholds: {
    http_req_failed: ["rate<0.01"],                                   // < 1% errors
    http_req_duration: ["p(95)<800", "max<3000"],                      // overall latency
    "http_req_duration{endpoint:findByStatus}": ["p(95)<500"],         // per-endpoint SLA
    "http_req_duration{endpoint:createPet}": ["p(95)<1000"],
    checks: ["rate>0.99"],                                            // functional checks under load
    pets_sold: ["count>0"],                                           // the write flow really ran
  },

  // Keep metric cardinality low in InfluxDB/Grafana: don't tag by full URL (pet ids differ per call)
  systemTags: ["status", "method", "name", "scenario", "check", "error_code", "expected_response"],
};

const petsSold = new Counter("pets_sold");
const timeToSell = new Trend("time_to_sell_ms", true);

const params = (endpoint) => ({
  headers: { "Content-Type": "application/json", Accept: "application/json" },
  tags: { endpoint, name: endpoint },
});

// ---------- scenario 1: read traffic ----------
export function browse() {
  group("browse catalogue", () => {
    const res = http.get(`${BASE_URL}/pet/findByStatus?status=available`, params("findByStatus"));
    check(res, {
      "findByStatus is 200": (r) => r.status === 200,
      "findByStatus returns a list": (r) => Array.isArray(r.json()),
    });

    sleep(Math.random() * 0.6 + 0.2); // think time 0.2 - 0.8 s

    const inv = http.get(`${BASE_URL}/store/inventory`, params("inventory"));
    check(inv, { "inventory is 200": (r) => r.status === 200 });
  });
  sleep(1);
}

// ---------- scenario 2: write traffic ----------
export function registerAndSell() {
  const id = Math.floor(10_000_000 + Math.random() * 89_999_999);
  const pet = { id, name: `k6-${id}`, photoUrls: [], status: "available" };
  const started = Date.now();

  group("register and sell a pet", () => {
    const created = http.post(`${BASE_URL}/pet`, JSON.stringify(pet), params("createPet"));
    const ok = check(created, {
      "createPet is 200": (r) => r.status === 200,
      "createPet echoes the id": (r) => r.status === 200 && r.json("id") === id,
    });
    if (!ok) return;

    const fetched = http.get(`${BASE_URL}/pet/${id}`, params("getPet"));
    check(fetched, { "getPet is 200": (r) => r.status === 200 });

    const sold = http.put(`${BASE_URL}/pet`, JSON.stringify({ ...pet, status: "sold" }), params("updatePet"));
    if (check(sold, { "updatePet sets sold": (r) => r.status === 200 && r.json("status") === "sold" })) {
      petsSold.add(1);
      timeToSell.add(Date.now() - started);
    }
  });
}

// ---------- reporting: console summary + JSON + JUnit XML for the CI "Tests" tab ----------
export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: " ", enableColors: true }),
    "results/k6-summary.json": JSON.stringify(data, null, 2),
    "results/k6-junit.xml": jUnit(data),
  };
}
