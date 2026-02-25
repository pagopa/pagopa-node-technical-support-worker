package it.gov.pagopa.nodetsworker.service;

import it.gov.pagopa.nodetsworker.repository.PositionPaymentSnapshotReader;
import it.gov.pagopa.nodetsworker.repository.qualifiers.DefaultNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class NodeSnapshotRouter {

    @Inject
    Logger log;

    @Inject
    @DefaultNode
    PositionPaymentSnapshotReader defaultReader;

    public PositionPaymentSnapshotReader resolve(String serviceIdentifier) {
        // Multi-node setup:
    	// - today: always returns default reader, as we have only one node
    	// - in the future: logic to determine the correct reader based on serviceIdentifier 
    	//   (e.g., mapping serviceIdentifier -> nodeKey in application.properties, like snapshot.node-map.NDP003PROD=default etc.)
        if (serviceIdentifier == null || serviceIdentifier.isBlank()) {
            return defaultReader;
        }
        
        return defaultReader;
    }
}
