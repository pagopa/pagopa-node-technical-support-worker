package it.gov.pagopa.nodetsworker.observability.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CacheVersions", description = "Cache version of NDP001 and NDP004 read from Nodo monitoring")
public class CacheVersionsDto {
  @Schema(required = true, format = "date-time",
      pattern = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$")
  private String checkedAt;
  @Schema(required = true, description = "One entry per node, NDP001 first; a failing node does not hide the others")
  private List<Node> nodes;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "CacheVersionNode")
  public static class Node {
    @Schema(required = true, enumeration = {"NDP001", "NDP004"})
    private String node;
    @Schema(required = true, description = "X-Ndp-Target sent to Nodo monitoring")
    private String target;
    @Schema(required = true, enumeration = {"OK", "UNCONFIGURED", "UNAVAILABLE"},
        description = "UNCONFIGURED: no subscription key; UNAVAILABLE: call failed or timed out")
    private String outcome;
    @Schema(required = true, nullable = true)
    private String dbStatus;
    @Schema(required = true, nullable = true)
    private String cacheId;
    @Schema(required = true, nullable = true)
    private String version;
    @Schema(required = true, nullable = true)
    private String environment;
    @Schema(required = true, nullable = true)
    private String serviceIdentifier;
  }
}
