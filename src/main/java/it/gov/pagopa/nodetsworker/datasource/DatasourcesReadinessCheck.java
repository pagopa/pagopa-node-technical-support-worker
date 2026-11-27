package it.gov.pagopa.nodetsworker.datasource;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import java.sql.SQLException;

/**
 * Replaces the Agroal readiness check ({@code quarkus.datasource.health.enabled=false}), which would
 * create a pool for every declared datasource: only enabled, non-excluded datasources are validated.
 */
@Readiness
@ApplicationScoped
public class DatasourcesReadinessCheck implements HealthCheck {

    static final String NAME = "Database connections health check";

    @Inject
    DatasourceRegistry registry;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named(NAME).up();
        for (String name : registry.healthChecked()) {
            try {
                if (((AgroalDataSource) registry.dataSource(name)).isHealthy(false)) {
                    builder.withData(name, "UP");
                } else {
                    builder.down().withData(name, "validation check failed for DataSource '" + name + "'");
                }
            } catch (SQLException | RuntimeException e) {
                builder.down().withData(name,
                        "Unable to execute the validation check for DataSource '" + name + "': " + e.getMessage());
            }
        }
        return builder.build();
    }
}
