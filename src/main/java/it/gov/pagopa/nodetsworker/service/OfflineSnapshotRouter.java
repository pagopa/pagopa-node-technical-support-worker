package it.gov.pagopa.nodetsworker.service;

import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.runtime.Startup;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Locale;

/**
 * Routes offline snapshot queries to the persistence unit of the requested node.
 * offline-ndp004 is the default persistence unit, so requests without a service identifier
 * keep the historical behaviour (offline-ndp004, labelled with {@code db.serviceIdentifier}).
 */
@Startup
@ApplicationScoped
public class OfflineSnapshotRouter {

    public record OfflineNode(EntityManager entityManager, String serviceIdentifier) {
    }

    private final OfflineNode defaultNode;
    private final OfflineNode ndp001Node;
    private final OfflineNode ndp004Node;
    private final String ndp001Key;
    private final String ndp004Key;

    @Inject
    public OfflineSnapshotRouter(
            @ConfigProperty(name = "snapshot.offline.nodes.offline-ndp001.service-identifier", defaultValue = "")
            String ndp001ServiceIdentifier,
            @ConfigProperty(name = "snapshot.offline.nodes.offline-ndp004.service-identifier", defaultValue = "")
            String ndp004ServiceIdentifier,
            @ConfigProperty(name = "db.serviceIdentifier") String defaultServiceIdentifier,
            @PersistenceUnit("offline-ndp001") EntityManager ndp001EntityManager,
            EntityManager ndp004EntityManager) {
        this.ndp001Key = normalize(ndp001ServiceIdentifier);
        this.ndp004Key = normalize(ndp004ServiceIdentifier);
        if (ndp001Key.isEmpty() || ndp004Key.isEmpty() || ndp001Key.equals(ndp004Key)) {
            throw new IllegalStateException(
                    "Offline snapshot datasource service identifiers must be non-blank and distinct");
        }
        this.ndp001Node = new OfflineNode(ndp001EntityManager, ndp001ServiceIdentifier.strip());
        this.ndp004Node = new OfflineNode(ndp004EntityManager, ndp004ServiceIdentifier.strip());
        this.defaultNode = new OfflineNode(ndp004EntityManager, defaultServiceIdentifier);
    }

    public OfflineNode resolve(String serviceIdentifier) {
        if (serviceIdentifier == null || serviceIdentifier.isBlank()) {
            return defaultNode;
        }
        String key = normalize(serviceIdentifier);
        if (ndp001Key.equals(key)) {
            return ndp001Node;
        }
        if (ndp004Key.equals(key)) {
            return ndp004Node;
        }
        throw new AppException(AppErrorCodeMessageEnum.BAD_REQUEST);
    }

    private static String normalize(String serviceIdentifier) {
        return serviceIdentifier == null ? "" : serviceIdentifier.strip().toLowerCase(Locale.ROOT);
    }
}
