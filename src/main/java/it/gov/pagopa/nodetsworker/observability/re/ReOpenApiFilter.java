package it.gov.pagopa.nodetsworker.observability.re;

import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Schema;

/** SmallRye infers object for the scalar marker class; remove that incompatible constraint. */
public class ReOpenApiFilter implements OASFilter {
  @Override
  public void filterOpenAPI(OpenAPI api) {
    if (api.getComponents() == null || api.getComponents().getSchemas() == null) {
      return;
    }
    var schemas = api.getComponents().getSchemas();
    scalar(schemas.get("ReScalar"));
    Schema event = schemas.get("ReEvent");
    if (event != null && event.getProperties() != null) {
      for (String field : ReEvent.FIELDS) {
        scalar(event.getProperties().get(field));
      }
    }
  }

  private void scalar(Schema schema) {
    if (schema != null && schema.getOneOf() != null && schema.getOneOf().size() == 3) {
      // Drop the inferred object type (and its "null" member: alone, OpenAPI 3.0 output would turn it
      // into "enum: [null]"). Null must match exactly one branch: the document is written as OpenAPI 3.0.3
      // (quarkus.smallrye-openapi.open-api-version), where the branch becomes "nullable: true".
      schema.setType(null);
      schema.getOneOf().get(0).addType(Schema.SchemaType.NULL);
    }
  }
}
