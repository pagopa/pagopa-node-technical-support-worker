package it.gov.pagopa.nodetsworker.resources.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.*;
import lombok.extern.jackson.Jacksonized;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Builder
@Jacksonized
@JsonPropertyOrder({"name", "version", "environment", "description", "errorCodes"})
public class InfoResponse {
  @Schema(example = "pagopa-node-tech-support")
  private String name;

  @Schema(example = "1.2.3")
  private String version;

  @Schema(example = "dev")
  private String environment;

  @Schema(example = "Node tech support API")
  private String description;

  private List<ErrorCode> errorCodes;

  @Builder
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ErrorCode {
    @Schema(example = "0500")
    private String code;

    @Schema(example = "An unexpected error has occurred. Please contact support.")
    private String description;

    @Schema(example = "500")
    private int statusCode;
  }
}
