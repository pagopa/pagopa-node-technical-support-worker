package it.gov.pagopa.nodetsworker.service;

import io.quarkus.runtime.Startup;
import it.gov.pagopa.nodetsworker.datasource.DatasourceDisabledException;
import it.gov.pagopa.nodetsworker.datasource.DatasourceRegistry;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.repository.JdbcPositionPaymentSnapshotReader;
import it.gov.pagopa.nodetsworker.repository.PositionPaymentSnapshotReader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Startup
@ApplicationScoped
public class NodeSnapshotRouter {

    private final DatasourceRegistry datasources;
    private final String ndp001ServiceIdentifier;
    private final String ndp004ServiceIdentifier;
    private final ConcurrentMap<String, PositionPaymentSnapshotReader> readers = new ConcurrentHashMap<>();

    @Inject
    public NodeSnapshotRouter(
            @ConfigProperty(name = "snapshot.online.nodes.online-ndp001.service-identifier", defaultValue = "")
            String ndp001ServiceIdentifier,
            @ConfigProperty(name = "snapshot.online.nodes.online-ndp004.service-identifier", defaultValue = "")
            String ndp004ServiceIdentifier,
            DatasourceRegistry datasources) {
        this.ndp001ServiceIdentifier = normalize(ndp001ServiceIdentifier);
        this.ndp004ServiceIdentifier = normalize(ndp004ServiceIdentifier);
        validateAssociations(this.ndp001ServiceIdentifier, this.ndp004ServiceIdentifier);
        this.datasources = datasources;
    }

    public PositionPaymentSnapshotReader resolve(String serviceIdentifier) {
        if (serviceIdentifier == null || serviceIdentifier.isBlank()) {
            throw new AppException(AppErrorCodeMessageEnum.BAD_REQUEST);
        }
        String datasource = datasourceName(serviceIdentifier);
        javax.sql.DataSource dataSource = enabledDataSource(datasource);
        return readers.computeIfAbsent(datasource, name -> new JdbcPositionPaymentSnapshotReader(dataSource));
    }

    public javax.sql.DataSource resolveDataSource(String serviceIdentifier) {
        return enabledDataSource(datasourceName(serviceIdentifier));
    }

    private String datasourceName(String serviceIdentifier) {
        String normalizedIdentifier = normalize(serviceIdentifier);
        if (ndp001ServiceIdentifier.equals(normalizedIdentifier)) {
            return DatasourceRegistry.ONLINE_NDP001;
        }
        if (ndp004ServiceIdentifier.equals(normalizedIdentifier)) {
            return DatasourceRegistry.ONLINE_NDP004;
        }
        throw new AppException(AppErrorCodeMessageEnum.BAD_REQUEST);
    }

    private javax.sql.DataSource enabledDataSource(String datasource) {
        try {
            return datasources.dataSource(datasource);
        } catch (DatasourceDisabledException disabled) {
            throw new AppException(disabled, AppErrorCodeMessageEnum.DATASOURCE_UNAVAILABLE, datasource);
        }
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
