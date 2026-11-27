package it.gov.pagopa.nodetsworker.observability.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contract schema {@code Catalog}: enumerations and distinct dimension values for building filters. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogDto {
  private List<String> logicalStates;
  private List<String> attemptStates;
  private List<String> warningStates;
  private String unknownFilterValue;
  private List<String> serviceIdentifiers;
  private List<String> psps;
  private List<String> canali;
  private List<String> stazioni;
  private String aggregateFilterPolicy;
}
