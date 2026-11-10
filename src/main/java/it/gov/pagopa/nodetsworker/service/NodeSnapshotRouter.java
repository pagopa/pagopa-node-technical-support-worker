package it.gov.pagopa.nodetsworker.service;

import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.Startup;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.repository.JdbcPositionPaymentSnapshotReader;
import it.gov.pagopa.nodetsworker.repository.PositionPaymentSnapshotReader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Locale;

@Startup
@ApplicationScoped
public class NodeSnapshotRouter {

    private final PositionPaymentSnapshotReader ndp001Reader;
    private final PositionPaymentSnapshotReader ndp004Reader;
    private final javax.sql.DataSource ndp001DataSource;
    private final javax.sql.DataSource ndp004DataSource;
    private final String ndp001ServiceIdentifier;
    private final String ndp004ServiceIdentifier;

    @Inject
    public NodeSnapshotRouter(
            @ConfigProperty(name = "snapshot.online.nodes.online-ndp001.service-identifier", defaultValue = "")
            String ndp001ServiceIdentifier,
            @ConfigProperty(name = "snapshot.online.nodes.online-ndp004.service-identifier", defaultValue = "")
            String ndp004ServiceIdentifier,
            @DataSource("online-ndp001") javax.sql.DataSource ndp001DataSource,
            @DataSource("online-ndp004") javax.sql.DataSource ndp004DataSource) {
        this.ndp001ServiceIdentifier = normalize(ndp001ServiceIdentifier);
        this.ndp004ServiceIdentifier = normalize(ndp004ServiceIdentifier);
        validateAssociations(this.ndp001ServiceIdentifier, this.ndp004ServiceIdentifier);
        this.ndp001DataSource = ndp001DataSource;
        this.ndp004DataSource = ndp004DataSource;
        this.ndp001Reader = new JdbcPositionPaymentSnapshotReader(ndp001DataSource);
        this.ndp004Reader = new JdbcPositionPaymentSnapshotReader(ndp004DataSource);
    }

    public PositionPaymentSnapshotReader resolve(String serviceIdentifier) {
        if (serviceIdentifier == null || serviceIdentifier.isBlank()) {
            throw new AppException(AppErrorCodeMessageEnum.BAD_REQUEST);
        }

        String normalizedIdentifier = normalize(serviceIdentifier);
        if (ndp001ServiceIdentifier.equals(normalizedIdentifier)) {
            return ndp001Reader;
        }
        if (ndp004ServiceIdentifier.equals(normalizedIdentifier)) {
            return ndp004Reader;
        }
        throw new AppException(AppErrorCodeMessageEnum.BAD_REQUEST);
    }

    public javax.sql.DataSource resolveDataSource(String serviceIdentifier) {
        String normalizedIdentifier = normalize(serviceIdentifier);
        if (ndp001ServiceIdentifier.equals(normalizedIdentifier)) {
            return ndp001DataSource;
        }
        if (ndp004ServiceIdentifier.equals(normalizedIdentifier)) {
            return ndp004DataSource;
        }
        throw new AppException(AppErrorCodeMessageEnum.BAD_REQUEST);
    }

    private static void validateAssociations(String ndp001ServiceIdentifier, String ndp004ServiceIdentifier) {
        if (ndp001ServiceIdentifier.isEmpty()
                || ndp004ServiceIdentifier.isEmpty()
                || ndp001ServiceIdentifier.equals(ndp004ServiceIdentifier)) {
            throw new IllegalStateException(
                    "Snapshot datasource service identifiers must be non-blank and distinct");
        }
    }

    private static String normalize(String serviceIdentifier) {
        return serviceIdentifier == null ? "" : serviceIdentifier.strip().toLowerCase(Locale.ROOT);
    }
}
