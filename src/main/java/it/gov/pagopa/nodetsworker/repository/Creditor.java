package it.gov.pagopa.nodetsworker.repository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Creditor {
  private String idPA;
  private String idBrokerPA;
  private String idStation;
  private String companyName;
  private String officeName;
}
