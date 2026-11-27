package it.gov.pagopa.nodetsworker.datasource;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.Config;

import javax.sql.DataSource;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Single entry point to the named datasources, honouring {@code quarkus.datasource.<name>.active}.
 * A datasource is resolved (and its pool created) only on first use and only when active; an inactive
 * one raises {@link DatasourceDisabledException} and is never replaced by another datasource.
 */
@Singleton
public class DatasourceRegistry {

    public static final String OFFLINE_NDP004 = "offline-ndp004";
    public static final String OFFLINE_NDP001 = "offline-ndp001";
    public static final String ONLINE_NDP001 = "online-ndp001";
    public static final String ONLINE_NDP004 = "online-ndp004";
    public static final String OBSERVABILITY = "observability";

    private static final String DATASOURCE_PREFIX = "quarkus.datasource.";
    private static final String ACTIVE = "active";
    private static final String ACTIVE_SUFFIX = "." + ACTIVE;

    /** Quarkus datasource configuration groups that are not datasource names. */
    private static final Set<String> RESERVED = Set.of("health", "metrics", "devservices", "jdbc", "reactive");

    private final Predicate<String> enabled;
    private final Function<String, DataSource> lookup;
    private final Set<String> names;
    private final Function<String, String> config;
    private final ConcurrentMap<String, DataSource> resolved = new ConcurrentHashMap<>();

    @Inject
    public DatasourceRegistry(Config config, @Any Instance<DataSource> dataSources) {
        this(name -> parseActive(config.getOptionalValue(key(name, ACTIVE), String.class).orElse(null)),
                name -> dataSources.select(new io.quarkus.agroal.DataSource.DataSourceLiteral(name)).get(),
                configuredNames(config),
                key -> config.getOptionalValue(key, String.class).orElse(null));
    }

    public DatasourceRegistry(Predicate<String> enabled, Function<String, DataSource> lookup,
            Set<String> names, Function<String, String> config) {
        this.enabled = enabled;
        this.lookup = lookup;
        this.names = Set.copyOf(names);
        this.config = config;
    }

    /** Datasources declaring the {@code active} flag. */
    public Set<String> names() {
        return names;
    }

    public boolean isEnabled(String name) {
        return enabled.test(name);
    }

    public void requireEnabled(String name) {
        if (!isEnabled(name)) {
            throw new DatasourceDisabledException(name);
        }
    }

    /** The datasource, created lazily; never called for a disabled datasource. */
    public DataSource dataSource(String name) {
        requireEnabled(name);
        return resolved.computeIfAbsent(name, lookup);
    }

    /** Enabled datasources not excluded from readiness ({@code quarkus.datasource.<name>.health-exclude}). */
    public Set<String> healthChecked() {
        Set<String> checked = new TreeSet<>();
        for (String name : names) {
            if (isEnabled(name) && !Boolean.parseBoolean(String.valueOf(config.apply(key(name, "health-exclude"))))) {
                checked.add(name);
            }
        }
        return checked;
    }

    void validateOnStartup(@Observes StartupEvent event) {
        for (String name : names) {
            String url = config.apply(key(name, "jdbc.url"));
            if (isEnabled(name) && (url == null || url.isBlank())) {
                throw new IllegalStateException("Datasource '" + name + "' is enabled but "
                        + key(name, "jdbc.url") + " is not configured");
            }
        }
    }

    /** A datasource without flag keeps the Quarkus default (active). */
    static boolean parseActive(String flag) {
        return flag == null || flag.isBlank() || Boolean.parseBoolean(flag.strip());
    }

    private static String key(String name, String property) {
        return DATASOURCE_PREFIX + name + "." + property;
    }

    private static Set<String> configuredNames(Config config) {
        Set<String> found = new TreeSet<>();
        for (String property : config.getPropertyNames()) {
            String unprofiled = property.startsWith("%") && property.indexOf('.') > 0
                    ? property.substring(property.indexOf('.') + 1)
                    : property;
            if (unprofiled.startsWith(DATASOURCE_PREFIX) && unprofiled.endsWith(ACTIVE_SUFFIX)) {
                String name = unprofiled.substring(DATASOURCE_PREFIX.length(),
                        unprofiled.length() - ACTIVE_SUFFIX.length());
                if (!name.isEmpty() && !name.contains(".") && !RESERVED.contains(name)) {
                    found.add(name.replace("\"", ""));
                }
            }
        }
        return found;
    }
}
