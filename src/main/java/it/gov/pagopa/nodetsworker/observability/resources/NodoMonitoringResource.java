package it.gov.pagopa.nodetsworker.observability.resources;

import it.gov.pagopa.nodetsworker.observability.dto.CacheVersionsDto;
import it.gov.pagopa.nodetsworker.observability.dto.ErrorDto;
import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import it.gov.pagopa.nodetsworker.observability.service.NodoMonitoringService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@NoStore
public class NodoMonitoringResource {
    @Inject
    NodoMonitoringService service;

    @GET
    @Path("/nodo-monitoring/cache/version")
    @Operation(operationId = "getCacheVersions", summary = "Cache version of NDP001 and NDP004",
            description = "Read-only. Calls Nodo monitoring once per node (X-Ndp-Target) in parallel and "
                    + "aggregates the results. A node that is unconfigured or unreachable is reported with its "
                    + "outcome and null cache fields, without failing the whole response.")
    @APIResponse(responseCode = "200", description = "One entry per node, including failed ones",
            content = @Content(schema = @Schema(implementation = CacheVersionsDto.class)))
    @APIResponse(responseCode = "400", description = "Query parameters are not accepted",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public CacheVersionsDto versions(@Context UriInfo uriInfo) {
        if (!uriInfo.getQueryParameters().isEmpty()) {
            throw Errors.invalidRequest();
        }
        return service.versions();
    }
}
