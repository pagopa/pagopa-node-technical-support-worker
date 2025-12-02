package it.gov.pagopa.nodetsworker.models.enums;

public enum PaymentStatus {
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    UNKNOWN("unknown");

    private final String value;

    PaymentStatus(String value) { this.value = value; }

    @com.fasterxml.jackson.annotation.JsonValue
    public String getValue() { return value; }
}

