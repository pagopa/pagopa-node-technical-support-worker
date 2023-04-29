package it.gov.pagopa.quarkustemplate.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


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
