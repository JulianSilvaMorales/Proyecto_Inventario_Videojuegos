package com.inventario.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaVerifier {

    private static final String GENERATED_HEADER =
            "GENERADO — no editar; modifique el JSON del modelo o la plantilla.";
    private static final String COLUMNS_SQL = """
            SELECT table_name, column_name, udt_name, is_nullable,
                   numeric_precision, numeric_scale
            FROM information_schema.columns
            WHERE table_schema = 'public'
            """;

    private final ObjectMapper objectMapper;

    public DatabaseSchemaVerifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void verify(JdbcTemplate jdbcTemplate, Path modelPath) {
        modelPath = resolveModelPath(modelPath);
        if (!Files.isRegularFile(modelPath)) {
            throw new IllegalStateException(
                    "No se puede validar el esquema: no se encontró el modelo JSON en "
                            + modelPath.toAbsolutePath());
        }

        JsonNode model;
        try {
            model = objectMapper.readTree(modelPath.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No se pudo leer el modelo JSON para validar el esquema: "
                            + modelPath.toAbsolutePath(),
                    exception);
        }

        if (!GENERATED_HEADER.equals(model.path("_cabecera").asText())) {
            throw new IllegalStateException(
                    "El modelo JSON resuelto no tiene la cabecera de archivo generado.");
        }

        Map<String, DatabaseColumn> actualColumns = new HashMap<>();
        jdbcTemplate.query(COLUMNS_SQL, resultSet -> {
            DatabaseColumn column = new DatabaseColumn(
                    resultSet.getString("table_name"),
                    resultSet.getString("column_name"),
                    resultSet.getString("udt_name"),
                    "YES".equals(resultSet.getString("is_nullable")),
                    (Integer) resultSet.getObject("numeric_precision"),
                    (Integer) resultSet.getObject("numeric_scale"));
            actualColumns.put(column.table() + "." + column.name(), column);
        });

        List<String> errors = compare(model.path("entidades"), actualColumns);
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "El esquema PostgreSQL no coincide con db/model/schema-resuelto.json: "
                            + String.join("; ", errors));
        }
    }

    Path resolveModelPath(Path modelPath) {
        if (modelPath.isAbsolute() || Files.isRegularFile(modelPath)) {
            return modelPath;
        }
        Path parentPath = Path.of("..").resolve(modelPath).normalize();
        return Files.isRegularFile(parentPath) ? parentPath : modelPath;
    }

    List<String> compare(JsonNode entities, Map<String, DatabaseColumn> actualColumns) {
        List<String> errors = new ArrayList<>();
        if (!entities.isArray() || entities.isEmpty()) {
            return List.of("el modelo no contiene entidades");
        }

        for (JsonNode entity : entities) {
            String table = entity.path("nombre").asText();
            if (table.isBlank()) {
                errors.add("hay una entidad sin nombre");
                continue;
            }
            for (JsonNode field : entity.path("campos")) {
                String columnName = field.path("nombre").asText();
                DatabaseColumn actual = actualColumns.get(table + "." + columnName);
                if (actual == null) {
                    errors.add("falta la columna " + table + "." + columnName);
                    continue;
                }
                String expectedType = field.path("tipo").asText();
                if (!matchesType(field, expectedType, actual)) {
                    errors.add(
                            table + "." + columnName + " tiene tipo " + actual.type()
                                    + " y el modelo requiere " + expectedType);
                }
                boolean expectedNullable = !field.path("pk").asBoolean(false)
                        && !field.path("obligatorio").asBoolean(false);
                if (expectedNullable != actual.nullable()) {
                    errors.add(
                            table + "." + columnName
                                    + " tiene nulabilidad distinta a la definida en el modelo");
                }
            }
        }
        return errors;
    }

    private boolean matchesType(JsonNode field, String expected, DatabaseColumn actual) {
        return switch (expected) {
            case "uuid" -> "uuid".equals(actual.type());
            case "texto" -> "text".equals(actual.type());
            case "entero" -> "int4".equals(actual.type());
            case "booleano" -> "bool".equals(actual.type());
            case "fecha" -> "date".equals(actual.type());
            case "fecha_hora_utc" -> "timestamptz".equals(actual.type());
            case "json" -> "jsonb".equals(actual.type());
            case "decimal" -> "numeric".equals(actual.type())
                    && numberEquals(field.path("precision").asInt(), actual.precision())
                    && numberEquals(field.path("escala").asInt(), actual.scale());
            default -> false;
        };
    }

    private boolean numberEquals(int expected, Integer actual) {
        return actual != null && expected == actual;
    }

    record DatabaseColumn(
            String table,
            String name,
            String type,
            boolean nullable,
            Integer precision,
            Integer scale) {
    }
}
