package it.gov.pagopa.nodetsworker.observability.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cursor-paginated envelope (schemas {@code PaymentPage}/{@code AttemptPage}/{@code CreditorPage}/{@code StationPage}). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageDto<T> {
  private List<T> items;
  private String nextCursor;
  private long snapshotRevision;
}
