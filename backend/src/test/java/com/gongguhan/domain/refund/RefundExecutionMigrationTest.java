package com.gongguhan.domain.refund;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class RefundExecutionMigrationTest {
    @Test void historicUnknownFailuresAreNotAssumedSafeToReplay() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE refunds(refund_id BIGINT PRIMARY KEY, participation_id BIGINT, status VARCHAR(20), refund_marker CHAR(1), UNIQUE(participation_id,refund_marker))");
            statement.execute("INSERT INTO refunds VALUES(1,1,'FAILED',NULL),(2,1,'FAILED',NULL),(3,2,'PROCESSING','Y'),(4,3,'COMPLETED','Y')");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V18__guard_ambiguous_refund_execution.sql"));
            var rows = statement.executeQuery("SELECT COUNT(*) FROM refunds WHERE execution_claimed=TRUE");
            rows.next(); assertThat(rows.getInt(1)).isEqualTo(3);
            rows = statement.executeQuery("SELECT COUNT(*) FROM refunds WHERE status='FAILED' AND refund_marker IS NULL");
            rows.next(); assertThat(rows.getInt(1)).isEqualTo(2);
            statement.execute("INSERT INTO refunds(refund_id, participation_id,status,refund_marker) VALUES(5,4,'PROCESSING','Y')");
            rows = statement.executeQuery("SELECT execution_claimed FROM refunds WHERE refund_id=5");
            rows.next(); assertThat(rows.getBoolean(1)).isFalse();
        }
    }
}
