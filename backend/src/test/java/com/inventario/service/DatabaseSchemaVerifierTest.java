package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DatabaseSchemaVerifierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DatabaseSchemaVerifier verifier = new DatabaseSchemaVerifier(objectMapper);

    @Test
    void acceptsModelWhenColumnTypesAndNullabilityMatch() throws Exception {
        JsonNode model = objectMapper.readTree("""
                {
                  "entidades": [{
                    "nombre": "producto",
                    "campos": [
                      {"nombre": "id", "tipo": "uuid", "pk": true},
                      {"nombre": "nombre", "tipo": "texto", "obligatorio": true},
                      {"nombre": "atributos", "tipo": "json", "obligatorio": true},
                      {"nombre": "precio", "tipo": "decimal", "precision": 19, "escala": 4}
                    ]
                  }]
                }
                """);
        Map<String, DatabaseSchemaVerifier.DatabaseColumn> actualColumns = Map.of(
                "producto.id", column("uuid", false, null, null),
                "producto.nombre", column("text", false, null, null),
                "producto.atributos", column("jsonb", false, null, null),
                "producto.precio", column("numeric", true, 19, 4));

        assertThat(verifier.compare(model.path("entidades"), actualColumns)).isEmpty();
    }

    @Test
    void reportsMissingAndMismatchedColumnsClearly() throws Exception {
        JsonNode model = objectMapper.readTree("""
                {
                  "entidades": [{
                    "nombre": "producto",
                    "campos": [
                      {"nombre": "id", "tipo": "uuid", "pk": true},
                      {"nombre": "nombre", "tipo": "texto", "obligatorio": true}
                    ]
                  }]
                }
                """);
        Map<String, DatabaseSchemaVerifier.DatabaseColumn> actualColumns = Map.of(
                "producto.id", column("text", true, null, null));

        assertThat(verifier.compare(model.path("entidades"), actualColumns))
                .contains(
                        "producto.id tiene tipo text y el modelo requiere uuid",
                        "producto.id tiene nulabilidad distinta a la definida en el modelo",
                        "falta la columna producto.nombre");
    }

    @Test
    void resolvesModelPathFromBackendWorkingDirectory() {
        assertThat(verifier.resolveModelPath(Path.of("db/model/schema-resuelto.json")))
                .isEqualTo(Path.of("../db/model/schema-resuelto.json").normalize());
    }

    private DatabaseSchemaVerifier.DatabaseColumn column(
            String type,
            boolean nullable,
            Integer precision,
            Integer scale) {
        return new DatabaseSchemaVerifier.DatabaseColumn(
                "producto", "campo", type, nullable, precision, scale);
    }
}
