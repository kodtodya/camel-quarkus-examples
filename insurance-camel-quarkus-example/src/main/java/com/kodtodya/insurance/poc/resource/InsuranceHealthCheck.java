package com.kodtodya.insurance.poc.resource;

import com.kodtodya.insurance.poc.service.BatchStateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * SmallRye Health checks for liveness and readiness.
 */
@ApplicationScoped
public class InsuranceHealthCheck {

    @Inject
    BatchStateService batchService;

    @Inject
    DataSource dataSource;

    @Liveness
    @ApplicationScoped
    public static class LivenessCheck implements HealthCheck {
        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.up("insurance-camel-poc-live");
        }
    }

    @Readiness
    @ApplicationScoped
    public static class ReadinessCheck implements HealthCheck {

        @Inject
        DataSource dataSource;

        @Override
        public HealthCheckResponse call() {
            try (Connection conn = dataSource.getConnection()) {
                boolean valid = conn.isValid(2);
                return valid
                    ? HealthCheckResponse.named("database-readiness").up()
                                        .withData("db", "H2").build()
                    : HealthCheckResponse.named("database-readiness").down()
                                        .withData("reason", "connection not valid").build();
            } catch (Exception e) {
                return HealthCheckResponse.named("database-readiness").down()
                        .withData("error", e.getMessage()).build();
            }
        }
    }
}
