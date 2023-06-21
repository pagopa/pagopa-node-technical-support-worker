package it.gov.pagopa.nodetsworker.resources.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import it.gov.pagopa.nodetsworker.resources.model.Metadata;
import it.gov.pagopa.nodetsworker.service.dto.EventDto;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Getter
@Builder
@Jacksonized
@JsonPropertyOrder({"metadata", "count", "data"})
public class FindEventsResponse {

  private Metadata metadata;

  @Schema(example = "100")
  private Long count;

  private List<EventDto> data;
}
