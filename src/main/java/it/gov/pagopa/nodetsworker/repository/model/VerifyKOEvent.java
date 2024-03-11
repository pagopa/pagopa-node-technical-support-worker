package it.gov.pagopa.nodetsworker.repository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VerifyKOEvent {
  private String version;
  private String id;
  private DebtorPosition debtorPosition;
  private Creditor creditor;
  private Psp psp;
  private Fault faultBean;
  private String diagnosticId;
  private String serviceIdentifier;
  private String partitionKey;
}
