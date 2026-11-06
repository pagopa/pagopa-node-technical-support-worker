package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.observability.dto.FreshnessDto;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.repository.models.ProjectionMetaEntity;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;

/** Builds the pipeline freshness indicator from the projection metadata row. */
@ApplicationScoped
public class FreshnessService {

  @Inject
  ProjectionRepository repository;

  public FreshnessDto current() {
    ProjectionMetaEntity meta = repository.meta();
    return FreshnessDto.builder()
        .status(meta.getFreshnessStatus())
        .lagSeconds(meta.getFreshnessLagSeconds())
        .gapsDetected(meta.isFreshnessGapsDetected())
        .lastProcessedAt(IsoTime.format(meta.getLastProcessedAt()))
        .lastPublishedAt(IsoTime.format(meta.getLastPublishedAt()))
        .observedAt(IsoTime.format(Instant.now()))
        .build();
  }
}
