package it.gov.pagopa.nodetsworker.datasource;

/** Raised when a datasource inactive ({@code quarkus.datasource.<name>.active=false}) is required. */
public class DatasourceDisabledException extends RuntimeException {

    private final String datasource;

    public DatasourceDisabledException(String datasource) {
        super("Datasource '" + datasource + "' is disabled");
        this.datasource = datasource;
    }

    public String getDatasource() {
        return datasource;
    }
}
