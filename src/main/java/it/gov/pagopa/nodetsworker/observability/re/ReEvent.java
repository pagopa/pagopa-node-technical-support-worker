package it.gov.pagopa.nodetsworker.observability.re;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.gov.pagopa.nodetsworker.observability.re.ReEvent.Scalar;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;

/** Flat, lossless scalar RE projection. Decoded content is plain text, never interpreted. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "ReEvent",
    requiredProperties = {"insertedTimestamp", "componente", "categoriaEvento", "sottoTipoEvento",
        "idDominio", "iuv", "ccp", "psp", "tipoVersamento", "tipoEvento", "fruitore", "erogatore",
        "stazione", "canale", "parametriSpecificiInterfaccia", "esito", "sessionId", "status", "payload",
        "info", "businessProcess", "fruitoreDescr", "erogatoreDescr", "pspDescr", "noticeNumber",
        "creditorReferenceId", "paymentToken", "sessionIdOriginal", "standIn", "serviceIdentifier",
        "dataOraEvento", "uniqueId", "version", "payloadDecoded", "payloadOmitted", "payloadSourceBytes"},
    properties = {
        @SchemaProperty(name = "insertedTimestamp", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "componente", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "categoriaEvento", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "sottoTipoEvento", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "idDominio", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "iuv", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "ccp", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "psp", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "tipoVersamento", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "tipoEvento", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "fruitore", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "erogatore", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "stazione", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "canale", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "parametriSpecificiInterfaccia", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "esito", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "sessionId", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "status", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "payload", type = SchemaType.STRING, nullable = true),
        @SchemaProperty(name = "info", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "businessProcess", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "fruitoreDescr", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "erogatoreDescr", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "pspDescr", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "noticeNumber", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "creditorReferenceId", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "paymentToken", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "sessionIdOriginal", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "standIn", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "serviceIdentifier", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "dataOraEvento", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "uniqueId", implementation = Scalar.class, nullable = true),
        @SchemaProperty(name = "version", implementation = Scalar.class, nullable = true)
    })
public record ReEvent(@JsonIgnore @Schema(hidden = true) Map<String, Object> fields,
                      Decoded payloadDecoded, boolean payloadOmitted,
                      @Schema(nullable = true) Long payloadSourceBytes) {
  public static final List<String> FIELDS = List.of(
      "insertedTimestamp", "componente", "categoriaEvento", "sottoTipoEvento", "idDominio",
      "iuv", "ccp", "psp", "tipoVersamento", "tipoEvento", "fruitore", "erogatore",
      "stazione", "canale", "parametriSpecificiInterfaccia", "esito", "sessionId", "status",
      "payload", "info", "businessProcess", "fruitoreDescr", "erogatoreDescr", "pspDescr",
      "noticeNumber", "creditorReferenceId", "paymentToken", "sessionIdOriginal", "standIn",
      "serviceIdentifier", "dataOraEvento", "uniqueId", "version");
  static final int MAX_DECODED_BYTES = 256 * 1024;
  private static final int MAX_ENCODED_CHARS = 4 * ((MAX_DECODED_BYTES + 2) / 3);

  @JsonAnyGetter
  @Schema(hidden = true)
  public Map<String, Object> fields() {
    return fields;
  }

  public static ReEvent from(Map<String, Object> source) {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (String name : FIELDS) {
      Object value = source.get(name);
      if (value != null && !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
        throw ReErrors.source();
      }
      fields.put(name, value);
    }
    Object raw = fields.get("payload");
    if (raw == null) {
      return event(fields, "ABSENT", null, null, false, null);
    }
    if (!(raw instanceof String payload)) {
      throw ReErrors.source();
    }
    long sourceBytes = utf8Length(payload);
    if (payload.length() > MAX_ENCODED_CHARS) {
      fields.put("payload", null);
      return event(fields, "TOO_LARGE", null, "PAYLOAD_LIMIT", true, sourceBytes);
    }
    if (payload.length() % 4 != 0) {
      return event(fields, "INVALID_BASE64", null, "INVALID_BASE64", false, sourceBytes);
    }
    int padding = payload.endsWith("==") ? 2 : payload.endsWith("=") ? 1 : 0;
    if ((long) payload.length() / 4 * 3 - padding > MAX_DECODED_BYTES) {
      fields.put("payload", null);
      return event(fields, "TOO_LARGE", null, "PAYLOAD_LIMIT", true, sourceBytes);
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(payload);
      if (!Base64.getEncoder().encodeToString(decoded).equals(payload)) {
        return event(fields, "INVALID_BASE64", null, "INVALID_BASE64", false, sourceBytes);
      }
    } catch (IllegalArgumentException invalid) {
      return event(fields, "INVALID_BASE64", null, "INVALID_BASE64", false, sourceBytes);
    }
    if (decoded.length > MAX_DECODED_BYTES) {
      fields.put("payload", null);
      return event(fields, "TOO_LARGE", null, "PAYLOAD_LIMIT", true, sourceBytes);
    }
    try {
      String text = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(decoded)).toString();
      return event(fields, "DECODED", text, null, false, sourceBytes);
    } catch (CharacterCodingException invalid) {
      return event(fields, "INVALID_UTF8", null, "INVALID_UTF8", false, sourceBytes);
    }
  }

  private static ReEvent event(Map<String, Object> fields, String status, String text, String reason,
      boolean omitted, Long sourceBytes) {
    return new ReEvent(Collections.unmodifiableMap(fields), new Decoded(status, text, reason), omitted, sourceBytes);
  }

  private static long utf8Length(String value) {
    long bytes = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c <= 0x7f) {
        bytes++;
      } else if (c <= 0x7ff) {
        bytes += 2;
      } else if (Character.isHighSurrogate(c) && i + 1 < value.length()
          && Character.isLowSurrogate(value.charAt(i + 1))) {
        bytes += 4;
        i++;
      } else {
        bytes += Character.isSurrogate(c) ? 1 : 3;
      }
    }
    return bytes;
  }

  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "RePayloadDecoded", requiredProperties = {"status", "text", "reason"})
  public record Decoded(
      @Schema(enumeration = {"ABSENT", "DECODED", "INVALID_BASE64", "INVALID_UTF8", "TOO_LARGE"})
      String status, @Schema(nullable = true) String text, @Schema(nullable = true) String reason) { }

  @Schema(name = "ReScalar", oneOf = {String.class, Double.class, Boolean.class}, nullable = true)
  public static class Scalar { }

  @Override
  public String toString() {
    return "ReEvent[redacted]";
  }
}
