package it.gov.pagopa.nodetsworker.observability.apiconfig.cache;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CacheVersion implements Serializable {

  @JsonProperty(required = true)
  private String version;
}
