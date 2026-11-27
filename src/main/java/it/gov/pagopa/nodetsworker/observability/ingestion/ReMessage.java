package it.gov.pagopa.nodetsworker.observability.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The Registro Eventi (RE) message as consumed from Event Hubs, limited to the fields the projection
 * reads (spec §4.1 "Mappatura dei campi"). Every other field — in particular the base64 {@code
 * payload} of interface events — is ignored and never deserialized. Transport keys ({@link #eventHub},
 * {@link #partitionId}, {@link #sequenceNumber}) are not part of the RE body: the consumer fills them
 * from the {@code EventContext} to support the dedup fallback when {@code uniqueId} is missing.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReMessage {

  /** Must equal {@code CAMBIO_STATO} for the message to be admitted (§4.1). */
  private String esito;

  /** {@code INTERFACCIA} events are dropped before any processing (§4.1). */
  private String categoriaEvento;

  /** {@code REQ} / {@code RESP} on interface events; counted as primitive calls, never projected. */
  private String sottoTipoEvento;

  /**
   * Correlates the REQ and RESP of one primitive call together with {@code tipoEvento}. Used only in a
   * transient work table and never persisted with the aggregates (spec §3.3.2).
   */
  private String sessionId;

  /** Prefixed state, e.g. {@code payment_PAYING} or {@code position_PAID}. */
  private String status;

  /** Application-level provenance supplied by the admitted state-change event, when available. */
  private String tipoEvento;

  /** Maps to {@code paFiscalCode}. */
  private String idDominio;

  /** Maps to {@code noticeNumber} (never {@code iuv}/{@code creditorReferenceId}). */
  private String noticeNumber;

  /** Present on {@code payment_*} events, absent on {@code position_*} (never {@code ccp}). */
  private String paymentToken;

  /** Local {@code Europe/Rome} event time {@code yyyy-MM-dd'T'HH:mm:ss.SSS}, converted to UTC (§4.1). */
  private String dataOraEvento;

  /**
   * RE parties of an interface event. Read only to classify a primitive call as inbound or outbound
   * relative to the Nodo (spec §3.3.2); never persisted.
   */
  private String fruitore;
  private String erogatore;

  private String serviceIdentifier;
  private String psp;
  private String stazione;
  private String canale;

  /**
   * Free-form provenance string of the event. On {@code receipt_recipient_*} events it carries the
   * recipient discriminator as {@code stazione_recipient=<value>}; read via {@link #recipientStation()}.
   */
  private String info;

  /** 64-bit content hash computed by the Nodo; the primary dedup key (§4.4). */
  private String uniqueId;

  // --- transport keys, filled by the consumer (not part of the RE body) -----
  private transient String eventHub;
  private transient String partitionId;
  private transient Long sequenceNumber;

  /**
   * The recipient discriminator parsed from {@code info} (token {@code stazione_recipient=<value>}),
   * or {@code null} when absent. A single unidentified recipient collapses onto the {@code null} key.
   */
  public String recipientStation() {
    if (info == null) {
      return null;
    }
    for (String token : info.split("[,;\\s]+")) {
      int eq = token.indexOf('=');
      if (eq > 0 && "stazione_recipient".equals(token.substring(0, eq))) {
        String value = token.substring(eq + 1).trim();
        return value.isEmpty() ? null : value;
      }
    }
    return null;
  }
}
