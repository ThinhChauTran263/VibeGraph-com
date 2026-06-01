package com.vibegraph.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class Neo4jMigrationRunner implements ApplicationRunner {

    private final Driver neo4jDriver;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Applying Neo4j schema migrations...");
        try {
            List<String> statements = loadStatements("db/migration/V1__init_schema.cypher");
            try (Session session = neo4jDriver.session()) {
                for (String stmt : statements) {
                    session.run(stmt);
                }
            }
            log.info("Neo4j schema migration complete — {} statements applied", statements.size());
        } catch (Exception e) {
            log.error("Neo4j schema migration failed", e);
            throw new RuntimeException("Failed to apply Neo4j schema migration", e);
        }
    }

    private List<String> loadStatements(String resourcePath) throws Exception {
        var resource = new ClassPathResource(resourcePath);
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                    continue;
                }
                current.append(line).append("\n");
                if (trimmed.endsWith(";")) {
                    String stmt = current.toString().trim();
                    stmt = stmt.substring(0, stmt.length() - 1).trim();
                    if (!stmt.isEmpty()) {
                        statements.add(stmt);
                    }
                    current.setLength(0);
                }
            }
        }

        if (!current.isEmpty()) {
            String remaining = current.toString().trim();
            if (!remaining.isEmpty()) {
                statements.add(remaining);
            }
        }

        return statements;
    }
}
