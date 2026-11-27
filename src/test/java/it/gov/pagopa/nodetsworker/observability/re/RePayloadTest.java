package it.gov.pagopa.nodetsworker.observability.re;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class RePayloadTest {
  @Test
  void preservesAll33FieldsNullsScalarsUnknownStatesAndNanoseconds() throws Exception {
    var fields = new LinkedHashMap<String, Object>();
    fields.put("insertedTimestamp", "2026-10-23T10:00:00.123456789Z");
    fields.put("dataOraEvento", "2026-10-23T10:00:00.987654321Z");
    fields.put("status", "FUTURE_NODO_STATE");
    fields.put("standIn", true);
    fields.put("version", 23);
    fields.put("payload", Base64.getEncoder().encodeToString("città ✓".getBytes(StandardCharsets.UTF_8)));
    var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(ReEvent.from(fields)));
    assertEquals(36, json.size());
    for (String field : ReEvent.FIELDS) {
      assertTrue(json.has(field), field);
    }
    assertEquals("2026-10-23T10:00:00.123456789Z", json.get("insertedTimestamp").asText());
    assertEquals("2026-10-23T10:00:00.987654321Z", json.get("dataOraEvento").asText());
    assertEquals("FUTURE_NODO_STATE", json.get("status").asText());
    assertTrue(json.get("standIn").isBoolean());
    assertTrue(json.get("version").isNumber());
    assertTrue(json.get("info").isNull());
    assertEquals("città ✓", json.at("/payloadDecoded/text").asText());
  }

  @Test
  void strictlyValidatesBase64AndUtf8WithoutParsingContent() {
    assertEquals("ABSENT", ReEvent.from(new LinkedHashMap<>()).payloadDecoded().status());
    for (String invalid : new String[]{"YQ", "Y Q==", "YQ==\n", "YR==", "$==="}) {
      assertEquals("INVALID_BASE64", event(invalid).payloadDecoded().status(), invalid);
      assertEquals(invalid, event(invalid).fields().get("payload"));
    }
    assertEquals("INVALID_UTF8", event("/w==").payloadDecoded().status());
    String markup = "<script>not executed</script>";
    assertEquals(markup, event(Base64.getEncoder().encodeToString(markup.getBytes(StandardCharsets.UTF_8)))
        .payloadDecoded().text());
  }

  @Test
  void omitsOversizedOriginalsBeforeDecodingAndRejectsNonScalarSchema() {
    String oversized = "A".repeat(349532);
    var event = event(oversized);
    assertEquals("TOO_LARGE", event.payloadDecoded().status());
    assertTrue(event.payloadOmitted());
    assertEquals(349532L, event.payloadSourceBytes());
    assertNull(event.fields().get("payload"));
    assertThrows(RuntimeException.class, () -> ReEvent.from(java.util.Map.of("info", java.util.List.of("bad"))));
    assertThrows(RuntimeException.class, () -> ReEvent.from(java.util.Map.of("payload", true)));
  }

  @Test
  void acceptsExactly256KiBButOmitsEvenOneExtraDecodedByte() {
    String exact = Base64.getEncoder().encodeToString(new byte[256 * 1024]);
    assertEquals("DECODED", event(exact).payloadDecoded().status());
    String extra = Base64.getEncoder().encodeToString(new byte[256 * 1024 + 1]);
    assertEquals("TOO_LARGE", event(extra).payloadDecoded().status());
    assertNull(event(extra).fields().get("payload"));
  }

  private ReEvent event(String payload) {
    return ReEvent.from(java.util.Map.of("payload", payload));
  }
}
