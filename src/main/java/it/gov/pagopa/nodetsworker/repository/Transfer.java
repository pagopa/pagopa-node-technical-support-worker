package it.gov.pagopa.nodetsworker.repository;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
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
