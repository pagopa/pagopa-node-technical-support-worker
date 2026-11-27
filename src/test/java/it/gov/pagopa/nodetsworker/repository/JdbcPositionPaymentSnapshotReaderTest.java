package it.gov.pagopa.nodetsworker.repository;

import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.repository.models.PositionPaymentSSEntity;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcPositionPaymentSnapshotReaderTest {

    private static final String CREATE_TABLE = """
            CREATE TABLE V_POSITION_PAYMENT_STATUS_SNAPSHOT (
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

    @Test
    void findByPaymentToken_shouldMapTheNewestMatchingRow() throws Exception {
        JdbcDataSource dataSource = dataSource("jdbc:h2:mem:reader-map;DB_CLOSE_DELAY=-1");
        Instant inserted = LocalDateTime.parse("2024-01-02T03:04:05")
                .atZone(ZoneId.systemDefault()).toInstant();
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
            statement.executeUpdate("""
                    INSERT INTO V_POSITION_PAYMENT_STATUS_SNAPSHOT VALUES
                    (1, 'old', 'old', 'old', 'token', 'OLD', NULL, NULL, 10, 'old', 'old'),
                    (2, 'pa', 'notice', 'creditor', 'token', 'PAID', TIMESTAMP '2024-01-02 03:04:05',
                     TIMESTAMP '2024-01-02 03:04:06', 20, 'insert-user', 'update-user')
                    """);
        }

        Optional<PositionPaymentSSEntity> result =
                new JdbcPositionPaymentSnapshotReader(dataSource).findByPaymentToken("token");

        assertTrue(result.isPresent());
        PositionPaymentSSEntity entity = result.orElseThrow();
        assertEquals(2L, entity.getId());
        assertEquals("pa", entity.getPaFiscalCode());
        assertEquals("notice", entity.getNoticeId());
        assertEquals("creditor", entity.getCreditorReferenceId());
        assertEquals("token", entity.getPaymentToken());
        assertEquals("PAID", entity.getStatus());
        assertEquals(inserted, entity.getInsertedTimestamp());
        assertEquals(LocalDateTime.parse("2024-01-02T03:04:06")
                .atZone(ZoneId.systemDefault()).toInstant(), entity.getUpdatedTimestamp());
        assertEquals(20L, entity.getFkPositionPayment());
        assertEquals("insert-user", entity.getInsertedBy());
        assertEquals("update-user", entity.getUpdatedBY());
    }

    @Test
    void findByPaymentToken_shouldReturnEmptyWhenNoMatchingTokenExists() throws Exception {
        JdbcDataSource dataSource = dataSource("jdbc:h2:mem:reader-empty;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
        }

        assertEquals(Optional.empty(),
                new JdbcPositionPaymentSnapshotReader(dataSource).findByPaymentToken("missing"));
    }

    @Test
    void findByPaymentToken_shouldSurfaceConnectionAcquisitionFailuresAsApplicationErrors() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection unavailable"));

        AppException exception = assertThrows(AppException.class,
                () -> new JdbcPositionPaymentSnapshotReader(dataSource).findByPaymentToken("token"));

        assertEquals(AppErrorCodeMessageEnum.ERROR, exception.getCodeMessage());
        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test
    void findByPaymentToken_shouldSurfaceSqlFailuresAsApplicationErrors() {
        JdbcDataSource dataSource = dataSource("jdbc:h2:mem:reader-sql-error;DB_CLOSE_DELAY=-1");

        AppException exception = assertThrows(AppException.class,
                () -> new JdbcPositionPaymentSnapshotReader(dataSource).findByPaymentToken("token"));

        assertEquals(AppErrorCodeMessageEnum.ERROR, exception.getCodeMessage());
        assertInstanceOf(SQLException.class, exception.getCause());
    }

    private JdbcDataSource dataSource(String url) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
