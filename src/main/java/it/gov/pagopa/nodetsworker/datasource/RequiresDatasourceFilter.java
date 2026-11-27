package it.gov.pagopa.nodetsworker.datasource;

import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityExceptionMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import java.util.Optional;

/** Rejects requests to {@link RequiresDatasource} endpoints whose datasources are disabled (after authentication). */
public class RequiresDatasourceFilter {

    @Inject
    DatasourceRegistry registry;

    @ServerRequestFilter(priority = Priorities.AUTHORIZATION)
    public Optional<Response> filter(ResourceInfo resource) {
        RequiresDatasource required = resource.getResourceMethod() == null ? null
                : resource.getResourceMethod().getAnnotation(RequiresDatasource.class);
        if (required == null && resource.getResourceClass() != null) {
            required = resource.getResourceClass().getAnnotation(RequiresDatasource.class);
        }
        if (required != null) {
            for (String name : required.value()) {
                if (!registry.isEnabled(name)) {
                    return Optional.of(new ObservabilityExceptionMapper().toResponse(Errors.datasourceUnavailable()));
                }
            }
        }
        return Optional.empty();
    }
}
