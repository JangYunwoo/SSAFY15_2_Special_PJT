package com.gongguhan.global.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class FlywayMigrationVersionTest {

    @Test
    void versionedMigrationNumbersMustBeUnique() throws Exception {
        Resource[] migrations = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*__*.sql");

        Map<String, Long> versionCounts = Arrays.stream(migrations)
                .collect(Collectors.groupingBy(
                        resource -> versionOf(resource.getFilename()),
                        Collectors.counting()));

        assertThat(versionCounts)
                .as("Flyway는 같은 버전의 마이그레이션이 둘 이상이면 애플리케이션 시작을 거부한다")
                .allSatisfy((version, count) -> assertThat(count)
                        .as("중복된 Flyway 버전: %s", version)
                        .isEqualTo(1L));
    }

    private String versionOf(String filename) {
        assertThat(filename).isNotNull();
        return filename.substring(1, filename.indexOf("__"));
    }
}
