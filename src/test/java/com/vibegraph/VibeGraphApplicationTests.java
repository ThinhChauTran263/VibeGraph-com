package com.vibegraph;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("@SpringBootTest nạp full context cần Neo4j thật (Driver bean + Neo4jMigrationRunner "
        + "chạy lúc startup); chưa có Testcontainers. Round-trip Neo4j đã được phủ bởi "
        + "Neo4jGraphRepositoryIT (tự skip khi không có DB). Enable khi thêm Testcontainers Neo4j.")
class VibeGraphApplicationTests {

    @Test
    void contextLoads() {
    }

}
