package it.gov.pagopa.nodetsworker.datasource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the datasources an endpoint needs: when any of them is disabled the request is rejected with
 * 503 before reaching the resource. A method annotation replaces the one on its class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresDatasource {
    String[] value();
}
