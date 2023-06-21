package it.gov.pagopa.nodetsworker.repository;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Psp {
  private String idBrokerPsp;
  private String idChannel;
  private String idPsp;
  private String psp;
  private String pspPartitaIVA;
  private String pspFiscalCode;
  private String channelDescription;
}
