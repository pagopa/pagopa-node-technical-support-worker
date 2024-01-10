package it.gov.pagopa.nodetsworker.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fault {
  private String faultCode;
  private String description;
  private Long timestamp;
  private String dateTime;
}
