package it.gov.pagopa.nodetsworker.service;

import io.quarkus.agroal.DataSource;
import io.quarkus.test.junit.QuarkusTest;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class OnlinePositionPaymentSnapshotReaderTest {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS V_POSITION_PAYMENT_STATUS_SNAPSHOT (
                ID BIGINT PRIMARY KEY,
                PA_FISCAL_CODE VARCHAR(20),
                NOTICE_ID VARCHAR(30),
                CREDITOR_REFERENCE_ID VARCHAR(30),
                PAYMENT_TOKEN VARCHAR(100),
                STATUS VARCHAR(30),
                INSERTED_TIMESTAMP TIMESTAMP,
                UPDATED_TIMESTAMP TIMESTAMP,
                FK_POSITION_PAYMENT BIGINT,
                INSERTED_BY VARCHAR(100),
                UPDATED_BY VARCHAR(100)
            )
            """;

    @Inject
    PositionPaymentSnapshotService service;

    @Inject
    @DataSource("online-ndp001")
    javax.sql.DataSource onlineDataSource;

    @Inject
    @DataSource("online-ndp004")
    javax.sql.DataSource ndp004prodDataSource;

    @BeforeEach
    void seedIndependentOnlineSources() throws SQLException {
        clear(onlineDataSource);
        clear(ndp004prodDataSource);
        insert(onlineDataSource, 101L, "shared-token", "NDP001-PAID");
        insert(ndp004prodDataSource, 202L, "shared-token", "NDP004-FAILED");
        insert(onlineDataSource, 303L, "only-online", "NDP001-ONLY");
        insert(ndp004prodDataSource, 404L, "only-ndp004", "NDP004-ONLY");
    }

    @Test
    void getByPaymentToken_shouldReadOnlyTheSourceSelectedByServiceIdentifier() {
        assertEquals("NDP001-PAID", service.getByPaymentToken("shared-token", " NDP001 ").getStatus());
        assertEquals("NDP004-FAILED", service.getByPaymentToken("shared-token", "ndp004prod").getStatus());
    }

    @Test
    void getByPaymentToken_shouldNotFallBackToTheOtherNodeWhenTheTokenIsMissing() {
        AppException onlineMissing = assertThrows(AppException.class,
                () -> service.getByPaymentToken("only-ndp004", "ndp001"));
        AppException ndp004Missing = assertThrows(AppException.class,
                () -> service.getByPaymentToken("only-online", "ndp004prod"));

        assertEquals(AppErrorCodeMessageEnum.NOT_FOUND, onlineMissing.getCodeMessage());
        assertEquals(AppErrorCodeMessageEnum.NOT_FOUND, ndp004Missing.getCodeMessage());
    }

    @Test
    void getByPaymentToken_shouldSurfaceSelectedNodeQueryErrorsWithoutFallback() throws SQLException {
        try (Connection connection = onlineDataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("DROP TABLE V_POSITION_PAYMENT_STATUS_SNAPSHOT");
        }

        AppException exception = assertThrows(AppException.class,
                () -> service.getByPaymentToken("shared-token", "ndp001"));

        assertEquals(AppErrorCodeMessageEnum.ERROR, exception.getCodeMessage());
    }

    private void clear(javax.sql.DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
            statement.executeUpdate("DELETE FROM V_POSITION_PAYMENT_STATUS_SNAPSHOT");
        }
    }

    private void insert(javax.sql.DataSource dataSource, long id, String token, String status) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO V_POSITION_PAYMENT_STATUS_SNAPSHOT (ID, PAYMENT_TOKEN, STATUS)
                     VALUES (?, ?, ?)
                     """)) {
            statement.setLong(1, id);
            statement.setString(2, token);
            statement.setString(3, status);
            statement.executeUpdate();
        }
    }
}
