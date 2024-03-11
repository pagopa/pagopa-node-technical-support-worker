package it.gov.pagopa.nodetsworker.models;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppInfo {
  private String name;
  private String version;
  private String environment;
}
