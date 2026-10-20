package com.inventario.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.domain.ParametroResuelto;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ParametroRepository {

    private static final String FIND_EFFECTIVE = """
            SELECT parametro.clave, parametro.valor::text AS valor, parametro.tipo,
                   parametro.minimo, parametro.maximo,
                   parametro.valor_por_defecto::text AS valor_por_defecto,
                   parametro.ambito, pais.codigo_iso AS pais_codigo,
                   parametro.vigente_desde, parametro.vigente_hasta
            FROM public.parametro
            LEFT JOIN public.pais ON pais.id = parametro.pais_id
            WHERE parametro.clave = ?
              AND parametro.vigente_desde <= CURRENT_DATE
              AND (parametro.vigente_hasta IS NULL
                   OR parametro.vigente_hasta >= CURRENT_DATE)
              AND (
                  (parametro.ambito = 'NEGOCIO' AND parametro.pais_id IS NULL)
                  OR (CAST(? AS text) IS NOT NULL AND parametro.ambito = 'PAIS'
                      AND pais.codigo_iso = ?)
              )
            ORDER BY CASE WHEN parametro.ambito = 'PAIS' THEN 0 ELSE 1 END,
                     parametro.vigente_desde DESC
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<ParametroResuelto> parameterRowMapper;

    public ParametroRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.parameterRowMapper = this::mapParameter;
    }

    public Optional<ParametroResuelto> buscarVigente(String clave, String paisCodigo) {
        List<ParametroResuelto> parameters = jdbcTemplate.query(
                FIND_EFFECTIVE,
                parameterRowMapper,
                clave,
                paisCodigo,
                paisCodigo);
        return parameters.stream().findFirst();
    }

    private ParametroResuelto mapParameter(ResultSet resultSet, int rowNumber)
            throws SQLException {
        try {
            return new ParametroResuelto(
                    resultSet.getString("clave"),
                    objectMapper.readTree(resultSet.getString("valor")),
                    resultSet.getString("tipo"),
                    resultSet.getBigDecimal("minimo"),
                    resultSet.getBigDecimal("maximo"),
                    objectMapper.readTree(resultSet.getString("valor_por_defecto")),
                    resultSet.getString("ambito"),
                    resultSet.getString("pais_codigo"),
                    resultSet.getObject("vigente_desde", LocalDate.class),
                    resultSet.getObject("vigente_hasta", LocalDate.class));
        } catch (JsonProcessingException exception) {
            throw new SQLException("No se pudo interpretar el JSON del parámetro.", exception);
        }
    }
}
