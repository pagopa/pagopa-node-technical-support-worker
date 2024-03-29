package it.gov.pagopa.nodetsworker.repository.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transfer {
  private String idTransfer;
  private String fiscalCodePA;
  private String companyName;
  private BigDecimal amount;
  private String transferCategory;
  private String remittanceInformation;
  private String IBAN;
  private String MBDAttachment;
  private Object metadata;
}
