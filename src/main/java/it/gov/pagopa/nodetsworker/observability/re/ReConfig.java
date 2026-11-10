package it.gov.pagopa.nodetsworker.observability.re;

import jakarta.inject.Singleton;
import java.net.URI;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** All deployment assertions are opt-in; none represent automatic ADX verification. */
@Singleton
public class ReConfig {
  @ConfigProperty(name = "observability.re.enabled", defaultValue = "false")
  boolean enabled;
  @ConfigProperty(name = "observability.re.schema-verified", defaultValue = "false")
  boolean schemaVerified;
  @ConfigProperty(name = "observability.re.timezone-verified", defaultValue = "false")
  boolean timezoneVerified;
  @ConfigProperty(name = "observability.re.retention-verified", defaultValue = "false")
  boolean retentionVerified;
  @ConfigProperty(name = "observability.re.ordering-verified", defaultValue = "false")
  boolean orderingVerified;
  @ConfigProperty(name = "observability.re.watermark-verified", defaultValue = "false")
  boolean watermarkVerified;
  @ConfigProperty(name = "observability.re.personal-data-approved", defaultValue = "false")
  boolean personalDataApproved;
  @ConfigProperty(name = "observability.re.endpoint")
  Optional<String> endpoint = Optional.empty();
  @ConfigProperty(name = "observability.re.client-id")
  Optional<String> clientId = Optional.empty();
  @ConfigProperty(name = "observability.re.client-secret")
  Optional<String> clientSecret = Optional.empty();
  @ConfigProperty(name = "observability.re.tenant-id")
  Optional<String> tenantId = Optional.empty();
  @ConfigProperty(name = "observability.re.database")
  Optional<String> database = Optional.empty();
  @ConfigProperty(name = "observability.re.table")
  Optional<String> table = Optional.empty();
  @ConfigProperty(name = "observability.re.row-id-column")
  Optional<String> rowIdColumn = Optional.empty();
  @ConfigProperty(name = "observability.re.retention-days", defaultValue = "0")
  int retentionDays;
  @ConfigProperty(name = "observability.re.page-size", defaultValue = "100")
  int pageSize = 100;
  @ConfigProperty(name = "observability.re.max-rows", defaultValue = "1000")
  int maxRows = 1000;
  @ConfigProperty(name = "observability.re.timeout-seconds", defaultValue = "30")
  int timeoutSeconds = 30;
  @ConfigProperty(name = "observability.re.cursor-ttl-seconds", defaultValue = "300")
  int cursorTtlSeconds = 300;
  @ConfigProperty(name = "observability.re.max-concurrent", defaultValue = "4")
  int maxConcurrent = 4;
  @ConfigProperty(name = "observability.re.requests-per-minute", defaultValue = "60")
  int requestsPerMinute = 60;

  void requireReady() {
    if (!enabled || !schemaVerified || !timezoneVerified || !retentionVerified || !orderingVerified
        || !watermarkVerified || !personalDataApproved || !identifier(database)
        || !identifier(table) || !identifier(rowIdColumn)
        || retentionDays < 1 || retentionDays > 36500 || pageSize < 1 || pageSize > 100
        || maxRows < 1 || maxRows > 1000 || timeoutSeconds < 1 || timeoutSeconds > 120
        || cursorTtlSeconds < 1 || cursorTtlSeconds > 300 || maxConcurrent < 1 || maxConcurrent > 32
        || requestsPerMinute < 1 || requestsPerMinute > 600 || endpoint.isEmpty()
        || clientId.filter(value -> !value.isBlank()).isEmpty()
        || clientSecret.filter(value -> !value.isBlank()).isEmpty()
        || tenantId.filter(value -> !value.isBlank()).isEmpty()) {
      throw ReErrors.config();
    }
    try {
      URI uri = URI.create(endpoint.orElseThrow());
      if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
          || uri.getQuery() != null || uri.getFragment() != null
          || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
        throw ReErrors.config();
      }
    } catch (IllegalArgumentException invalid) {
      throw ReErrors.config();
    }
  }

  String binding() {
    return endpoint.orElse("") + "|" + database.orElse("") + "|" + table.orElse("") + "|"
        + rowIdColumn.orElse("") + "|" + retentionDays + "|" + pageSize + "|" + maxRows
        + "|" + timeoutSeconds + "|" + cursorTtlSeconds + "|5242880|262144";
  }

  private boolean identifier(Optional<String> value) {
    return value.isPresent() && value.get().matches("[A-Za-z_][A-Za-z0-9_]{0,127}");
  }
}
