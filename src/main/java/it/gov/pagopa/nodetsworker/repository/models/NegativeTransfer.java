package it.gov.pagopa.nodetsworker.repository.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegativeTransfer {
  private String idTransfer;
  private String fiscalCodePA;
  private String companyName;
  private BigDecimal amount;
  private String transferCategory;
  private String remittanceInformation;
  private String IBAN;
  private Boolean MBD;
  private Object metadata;
}
