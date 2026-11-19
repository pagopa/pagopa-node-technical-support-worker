package it.gov.pagopa.nodetsworker.resources;

import it.gov.pagopa.nodetsworker.models.PositionPaymentSnapshotDto;
import it.gov.pagopa.nodetsworker.service.PositionPaymentSnapshotService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/paymentToken")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "SecurityScheme")
@Tag(name = "Observability")
public class PositionPaymentSnapshotResource {

    @Inject
    PositionPaymentSnapshotService service;

    @GET
    @Path("/{paymentToken}")
    public PositionPaymentSnapshotDto getByPaymentToken(
            @PathParam("paymentToken") String paymentToken,
            @QueryParam("serviceIdentifier") String serviceIdentifier
    ) {
        return service.getByPaymentToken(paymentToken, serviceIdentifier);
    }
}