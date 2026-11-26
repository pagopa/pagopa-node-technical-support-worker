package it.gov.pagopa.nodetsworker.datasource;

import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfflineDatasourceStartupIT {

    @RegisterExtension
    static final QuarkusUnitTest quarkus = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .withApplicationRoot(jar -> jar.addClasses(OfflineDatasourceProbe.class, OfflineEntity.class))
            .overrideConfigKey("%test.quarkus.datasource.offline-ndp004.jdbc.url",
                    "jdbc:h2:tcp://127.0.0.1:1/nodo")
            .overrideConfigKey("%test.quarkus.datasource.offline-ndp004.jdbc.acquisition-timeout", "1S")
            .overrideConfigKey("%test.quarkus.datasource.offline-ndp001.jdbc.url",
                    "jdbc:h2:tcp://127.0.0.1:1/nodo-ndp001")
            .overrideConfigKey("%test.quarkus.datasource.offline-ndp001.jdbc.acquisition-timeout", "1S")
            .overrideConfigKey("quarkus.hibernate-orm.offline-ndp001.packages", OfflineEntity.class.getPackageName())
            .overrideConfigKey("%test.quarkus.hibernate-orm.offline-ndp001.database.generation", "none")
            .overrideConfigKey("%test.quarkus.hibernate-orm.offline-ndp001.sql-load-script", "no-file")
            .overrideConfigKey("quarkus.hibernate-orm.packages", OfflineEntity.class.getPackageName())
            .overrideConfigKey("%test.quarkus.hibernate-orm.database.generation", "none")
            .overrideConfigKey("%test.quarkus.hibernate-orm.sql-load-script", "no-file")
            .overrideConfigKey("quarkus.hibernate-orm.observability.active", "false")
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("mp.openapi.filter", "")
            .overrideConfigKey("quarkus.datasource.online-ndp001.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.online-ndp001.jdbc.url",
                    "jdbc:h2:mem:online-ndp001-startup")
            .overrideConfigKey("quarkus.datasource.online-ndp001.username", "sa")
            .overrideConfigKey("quarkus.datasource.online-ndp001.password", "");

    @Inject
    OfflineDatasourceProbe probe;

    @Test
    void startupDoesNotRequireOfflineDatabasesButDatabaseUseStillFails() throws Exception {
        assertEquals(1, probe.readOnlineDatabase());
        assertThrows(PersistenceException.class, probe::readOfflineDatabase);
        assertThrows(PersistenceException.class, probe::readOfflineNdp001Database);
    }

    @ApplicationScoped
    static class OfflineDatasourceProbe {
        @PersistenceContext
        EntityManager offlineEntityManager;

        @Inject
        @PersistenceUnit("offline-ndp001")
        EntityManager offlineNdp001EntityManager;

        @Inject
        @io.quarkus.agroal.DataSource("online-ndp001")
        DataSource onlineDataSource;

        int readOnlineDatabase() throws Exception {
            try (var connection = onlineDataSource.getConnection();
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("select 1")) {
                result.next();
                return result.getInt(1);
            }
        }

        @Transactional
        Object readOfflineDatabase() {
            return offlineEntityManager.createNativeQuery("select 1").getSingleResult();
        }

        @Transactional
        Object readOfflineNdp001Database() {
            return offlineNdp001EntityManager.createNativeQuery("select 1").getSingleResult();
        }
    }

    @Entity
    static class OfflineEntity {
        @Id
        public Long id;
    }

}
