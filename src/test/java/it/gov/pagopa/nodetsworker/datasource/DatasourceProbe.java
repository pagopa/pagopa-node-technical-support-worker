package it.gov.pagopa.nodetsworker.datasource;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableBean;
import io.quarkus.hibernate.orm.runtime.JPAConfig;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.TreeSet;

/** Test-only view on the Agroal pools and Hibernate persistence units actually started. */
final class DatasourceProbe {

    private DatasourceProbe() {
    }

    /** Names of the datasources whose Agroal pool bean is active and has been instantiated. */
    static Set<String> createdPools() {
        var container = Arc.container();
        var context = container.getActiveContext(ApplicationScoped.class);
        Set<String> names = new TreeSet<>();
        for (String name : container.instance(DatasourceRegistry.class).get().names()) {
            InjectableBean<AgroalDataSource> bean = container
                    .select(AgroalDataSource.class, new DataSource.DataSourceLiteral(name))
                    .getHandle().getBean();
            if (bean.isActive() && context.get(bean) != null) {
                names.add(name);
            }
        }
        return names;
    }

    static Set<String> deactivatedPersistenceUnits() {
        return new TreeSet<>(Arc.container().instance(JPAConfig.class).get().getDeactivatedPersistenceUnitNames());
    }
}
