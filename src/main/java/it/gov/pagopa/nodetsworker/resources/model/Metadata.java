package it.gov.pagopa.nodetsworker.resources.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Metadata {

  @Schema(example = "25")
  private int pageSize;

  @Schema(example = "1")
  private int pageNumber;

  @Schema(example = "3")
  private int totPage;
}
