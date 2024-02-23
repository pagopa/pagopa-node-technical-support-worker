package it.gov.pagopa.nodetsworker.models;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaultBean {
    private String faultCode;
    private String description;
    private String timestamp;
}
