package it.gov.pagopa.nodetsworker.repository.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DebtorPosition {
  private String modelType;
  private String noticeNumber;
  private String iuv;
}
