package it.gov.pagopa.nodetsworker.observability.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error body shared by every operation (schemas {@code Error400}..{@code Error500}): always
 * {@code code} and {@code message}; {@code resync} / {@code fallback} appear only when relevant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDto {
  private String code;
  private String message;
  private Boolean resync;
  private String fallback;
}
