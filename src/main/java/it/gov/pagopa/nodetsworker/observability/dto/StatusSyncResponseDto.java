package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Outcome of an authoritative state synchronization; a missing source snapshot has a null state. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "StatusSyncResponse")
public class StatusSyncResponseDto {

  @Schema(description = "SYNCED when the projection changed, otherwise NOOP",
      enumeration = {"SYNCED", "NOOP"}, required = true)
  private String outcome;

  @Schema(description = "State returned by Nodo Online, or null when the source snapshot is missing",
      nullable = true)
  private String state;
}
