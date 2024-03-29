package it.gov.pagopa.nodetsworker.repository.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subject {
  private String fullName;
  private String entityUniqueIdentifierType;
  private String entityUniqueIdentifierValue;
  private String streetName;
  private String civicNumber;
  private String postalCode;
  private String city;
  private String stateProvinceRegion;
  private String country;
  private String eMail;
}
