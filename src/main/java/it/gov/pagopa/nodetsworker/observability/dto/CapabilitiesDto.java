package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Capabilities granted to the current identity ({@code Me.capabilities}). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapabilitiesDto {
  private boolean lookupToken;
  private boolean revealToken;
  private boolean mockControls;
}
