package com.inventario.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditoriaService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void registrar(
            UUID usuarioId,
            UUID paisId,
            String accion,
            String entidad,
            UUID registroId,
            Object anteriores,
            Object nuevos) {
        jdbcTemplate.update(
                """
                INSERT INTO public.auditoria
                    (id, usuario_id, pais_id, accion, entidad, registro_id,
                     datos_anteriores, datos_nuevos, fecha_utc)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)
                """,
                UUID.randomUUID(),
                usuarioId,
                paisId,
                accion,
                entidad,
                registroId,
                serializar(anteriores),
                serializar(nuevos),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    private String serializar(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar el registro de auditoría.", exception);
        }
    }
}
