package it.gov.pagopa.nodetsworker.observability.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import it.gov.pagopa.nodetsworker.observability.ingestion.AdmissionFilter.Admitted;
import it.gov.pagopa.nodetsworker.observability.ingestion.AdmissionFilter.Entity;
import org.junit.jupiter.api.Test;

/** Admission filter of the RE consumer (spec §4.1). */
class AdmissionFilterTest {

  private static ReMessage msg(String esito, String categoria, String status) {
    ReMessage m = new ReMessage();
    m.setEsito(esito);
    m.setCategoriaEvento(categoria);
    m.setStatus(status);
    return m;
  }

  @Test
  void admitsPaymentState() {
    Admitted a = AdmissionFilter.admit(msg("CAMBIO_STATO", "CAMBIO_STATO", "payment_PAYING"));
    assertEquals(Entity.PAYMENT, a.entity());
    assertEquals("PAYING", a.state());
  }

  @Test
  void preservesOptionalApplicationTriggerExactly() {
    ReMessage message = msg("CAMBIO_STATO", null, "payment_NOTIFIED");
    message.setTipoEvento("sendPaymentOutcomeV2");

    Admitted admitted = AdmissionFilter.admit(message);

    assertEquals("sendPaymentOutcomeV2", admitted.trigger());
  }

  @Test
  void admitsPositionState() {
    Admitted a = AdmissionFilter.admit(msg("CAMBIO_STATO", null, "position_PAID"));
    assertEquals(Entity.POSITION, a.entity());
    assertEquals("PAID", a.state());
  }

  @Test
  void dropsNonCambioStato() {
    assertNull(AdmissionFilter.admit(msg("OK", null, "payment_PAYING")));
  }

  @Test
  void dropsInterfaceEvents() {
    assertNull(AdmissionFilter.admit(msg("CAMBIO_STATO", "INTERFACCIA", "payment_PAYING")));
  }

  @Test
  void admitsReceiptRecipientState() {
    // Receipt-recipient events are now projected as a third entity (overrides the former exclusion).
    Admitted a = AdmissionFilter.admit(msg("CAMBIO_STATO", "CAMBIO_STATO", "receipt_recipient_NOTICE_SENT"));
    assertEquals(Entity.RECIPIENT, a.entity());
    assertEquals("NOTICE_SENT", a.state());
  }

  @Test
  void dropsPrefixlessStates() {
    assertNull(AdmissionFilter.admit(msg("CAMBIO_STATO", "CAMBIO_STATO", "RPT_ACCETTATA")));
  }

  @Test
  void dropsNullStatus() {
    assertNull(AdmissionFilter.admit(msg("CAMBIO_STATO", null, null)));
  }
}
