package it.gov.pagopa.quarkustemplate.resources;

import it.gov.pagopa.quarkustemplate.models.AppInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

@Path("")
@Produces(value = MediaType.APPLICATION_JSON)
public class Generic {
    @ConfigProperty(name = "quarkus.application.name", defaultValue = "")
    private String name;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "")
    private String version;

    @ConfigProperty(name = "quarkus.application.environment", defaultValue = "")
    private String environment;


    @Operation(hidden = true)
    @GET
    @Path("")
    public Response home(){
        return Response.seeOther(UriBuilder.fromUri("/swagger").build()).build();
    }

    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AppInfo.class))),
            @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = MediaType.APPLICATION_JSON))
    })
    @GET
    @Path("/info")
    public Response info() {
        AppInfo info = AppInfo.builder()
                .name(name)
                .version(version)
                .environment(environment)
                .build();
        return Response.ok(info).build();
    }
}
