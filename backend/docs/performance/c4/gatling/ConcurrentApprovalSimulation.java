package performance.c4;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * C4 HTTP verification scenario. It is intentionally kept outside production sources.
 * Required environment variables: C4_BASE_URL, C4_AUTH_TOKEN, C4_CASE_KEY, C4_USERS.
 */
public class ConcurrentApprovalSimulation extends Simulation {

    private static final String BASE_URL = requireEnv("C4_BASE_URL");
    private static final String AUTH_TOKEN = requireEnv("C4_AUTH_TOKEN");
    private static final String CASE_KEY = requireEnv("C4_CASE_KEY");
    private static final int USERS = Integer.parseInt(requireEnv("C4_USERS"));

    HttpProtocolBuilder protocol = http.baseUrl(BASE_URL)
            .disableWarmUp()
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .authorizationHeader("Bearer " + AUTH_TOKEN);

    ScenarioBuilder concurrentApproval = scenario("C4 Concurrent Approval After - VU " + USERS)
            .exec(http("POST review-decision")
                    .post("/api/v1/cases/review-decision")
                    .queryParam("caseKey", CASE_KEY)
                    .body(io.gatling.javaapi.core.CoreDsl.StringBody(
                            "{\"decision\":\"APPROVED\",\"memo\":\"C4 Gatling after\"}"))
                    .check(status().is(200)));

    {
        setUp(concurrentApproval.injectOpen(atOnceUsers(USERS)))
                .protocols(protocol)
                .assertions(global().failedRequests().count().is(0L));
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required");
        }
        return value;
    }
}
