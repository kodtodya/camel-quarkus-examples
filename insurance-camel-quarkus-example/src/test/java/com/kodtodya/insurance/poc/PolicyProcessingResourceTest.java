package com.kodtodya.insurance.poc;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Integration tests for the Insurance Policy processing REST API.
 * Runs against an embedded Quarkus instance with in-memory H2.
 */
@QuarkusTest
class PolicyProcessingResourceTest {

    @Test
    void testInfoEndpoint() {
        given()
            .when().get("/api/v1/policies/info")
            .then()
                .statusCode(200)
                .body("application", is("Insurance Policy Camel POC"))
                .body("framework", containsString("Quarkus"));
    }

    @Test
    void testActiveEndpoint_initiallyFalse() {
        given()
            .when().get("/api/v1/policies/process/active")
            .then()
                .statusCode(200)
                .body("active", is(false));
    }

    @Test
    void testAllBatchesEndpoint_empty() {
        given()
            .when().get("/api/v1/policies/process/status")
            .then()
                .statusCode(200)
                .body("total", notNullValue());
    }

    @Test
    void testMetricsSummaryEndpoint() {
        given()
            .when().get("/api/v1/policies/metrics/summary")
            .then()
                .statusCode(200)
                .body("prometheusEndpoint", is("/q/metrics"));
    }

    @Test
    void testHealthLiveness() {
        given()
            .when().get("/q/health/live")
            .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void testHealthReadiness() {
        given()
            .when().get("/q/health/ready")
            .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void testPrometheusMetrics() {
        given()
            .when().get("/q/metrics")
            .then()
                .statusCode(200)
                .contentType(containsString("text/plain"));
    }

    @Test
    void testStartProcessing_returnsAccepted() throws InterruptedException {
        given()
            .queryParam("triggeredBy", "TEST")
            .when().post("/api/v1/policies/process/start")
            .then()
                .statusCode(anyOf(is(202), is(409))); // 409 if previous test left a batch running
        
        // Wait for any running batch to avoid interfering with other tests
        Thread.sleep(2000);
    }

    @Test
    void testUnknownBatch_returns404() {
        given()
            .when().get("/api/v1/policies/process/status/BATCH-UNKNOWN")
            .then()
                .statusCode(404);
    }
}
