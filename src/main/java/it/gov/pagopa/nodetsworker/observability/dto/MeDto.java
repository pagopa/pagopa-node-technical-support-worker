package it.gov.pagopa.nodetsworker.observability.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contract schema {@code Me}: the caller identity, its visible creditors and capabilities. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeDto {
  private String id;
  private String role;
  private List<String> visiblePaFiscalCodes;
  private CapabilitiesDto capabilities;
  private String authentication;
}
