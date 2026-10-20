package com.inventario.config;

import com.inventario.service.DatabaseSchemaVerifier;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SchemaValidationConfig {

    @Bean
    ApplicationRunner verifyDatabaseSchema(
            JdbcTemplate jdbcTemplate,
            DatabaseSchemaVerifier verifier,
            @Value("${inventory.schema-model-path:db/model/schema-resuelto.json}") String modelPath) {
        return arguments -> verifier.verify(jdbcTemplate, Path.of(modelPath));
    }
}
