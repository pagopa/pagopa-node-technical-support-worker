package it.gov.pagopa.nodetsworker.observability.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Contract schema {@code DimensionRow}: state breakdown for one dimension value ({@code value} null = unavailable). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DimensionRowDto {
  @Schema(nullable = true)
  private String value;
  private List<StateCountDto> states;
}
