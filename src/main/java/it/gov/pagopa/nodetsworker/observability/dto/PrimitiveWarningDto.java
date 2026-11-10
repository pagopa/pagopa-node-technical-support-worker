package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Contract schema {@code PrimitiveWarning}: one primitive call left unanswered for more than 10 seconds.
 * Only request attributes; no payload, payment token, notice number or session id.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PrimitiveWarning", requiredProperties = {"calledAt", "psp", "canale", "paFiscalCode", "stazione"},
    additionalProperties = Schema.False.class)
public class PrimitiveWarningDto {
  @Schema(description = "Data-ora (UTC) della richiesta rimasta senza risposta entro 10 secondi.")
  private String calledAt;

  @Schema(nullable = true, description = "PSP della richiesta; null se non disponibile.")
  private String psp;

  @Schema(nullable = true, description = "Canale della richiesta; null se non disponibile.")
  private String canale;

  @Schema(nullable = true, description = "Ente creditore (idDominio) della richiesta; null se non disponibile.")
  private String paFiscalCode;

  @Schema(nullable = true, description = "Stazione della richiesta; null se non disponibile.")
  private String stazione;
}
