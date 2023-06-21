package it.gov.pagopa.nodetsworker.repository;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class Creditor {
  private String idPA;
  private String idBrokerPA;
  private String idStation;
  private String companyName;
  private String officeName;
}
