package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Contract schema {@code PrimitiveCount}: primitive requests, answered requests and warnings of one
 * {@code serviceIdentifier}, primitive and direction. In an {@code aggregate.delta} the values are signed differences.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PrimitiveCount", requiredProperties = {"serviceIdentifier", "primitive", "direction", "requests", "responses", "warnings"},
    additionalProperties = Schema.False.class)
public class PrimitiveCountDto {
  @Schema(nullable = true, description = "Identificativo di servizio; null se non disponibile.")
  private String serviceIdentifier;

  @Schema(description = "Nome originale della primitiva (tipoEvento RE), senza normalizzazione.")
  private String primitive;

  @Schema(enumeration = {"INBOUND", "OUTBOUND", "UNKNOWN"},
      description = "Direzione rispetto al Nodo dai campi RE fruitore/erogatore dell'evento REQ: INBOUND se il Nodo è"
          + " l'erogatore, OUTBOUND se è il fruitore, UNKNOWN altrimenti. Mai dedotta dal nome della primitiva.")
  private String direction;

  @Schema(description = "Richieste di primitiva nella finestra.")
  private long requests;

  @Schema(description = "Richieste con risposta ricevuta.")
  private long responses;

  @Schema(description = "Richieste senza risposta entro 10 secondi (tardiva o assente).")
  private long warnings;
}
