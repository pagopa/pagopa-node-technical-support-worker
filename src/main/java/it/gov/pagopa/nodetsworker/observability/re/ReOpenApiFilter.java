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
      schema.setType(null);
      // OpenAPI 3.0 needs nullable on one concrete branch: null must match exactly one branch.
      schema.getOneOf().get(0).setNullable(true);
    }
  }
}
