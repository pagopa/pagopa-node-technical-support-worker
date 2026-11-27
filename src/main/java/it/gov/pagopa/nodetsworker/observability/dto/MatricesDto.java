package it.gov.pagopa.nodetsworker.observability.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** {@code matrices}: attempt state × psp / × canale, and recipient state × stazione_recipient. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(requiredProperties = {"psp", "canale", "recipient"}, additionalProperties = Schema.False.class)
public class MatricesDto {
  private List<DimensionRowDto> psp;
  private List<DimensionRowDto> canale;
  private List<DimensionRowDto> recipient;
}
