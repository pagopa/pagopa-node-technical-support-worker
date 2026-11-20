package it.gov.pagopa.nodetsworker.observability.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Payment key and token used to request an authoritative Nodo Online attempt synchronization. */
@Data
@NoArgsConstructor
@Schema(name = "AttemptSyncRequest")
public class AttemptSyncRequestDto {

  @NotBlank
  @Pattern(regexp = "\\d{11}")
  @Schema(description = "Ente creditore fiscal code", required = true, minLength = 11, maxLength = 11,
      pattern = "\\d{11}", example = "90000000001")
  private String paFiscalCode;

  @NotBlank
  @Pattern(regexp = "\\d{18}")
  @Schema(description = "Notice number", required = true, minLength = 18, maxLength = 18,
      pattern = "\\d{18}", example = "790000000000000001")
  private String noticeNumber;

  @NotBlank
  @Size(max = 50)
  @Schema(description = "Payment token as stored by Nodo Online", required = true,
      minLength = 1, maxLength = 50)
  private String paymentToken;
}
