package com.inventario.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.domain.ParametroFila;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ParametroAdminRepository {

    private static final String FROM = """
            FROM public.parametro
            LEFT JOIN public.pais ON pais.id = parametro.pais_id
            """;

    private static final String COLUMNS = """
            SELECT parametro.id, parametro.clave, parametro.valor::text AS valor,
                   parametro.tipo, parametro.minimo, parametro.maximo,
                   parametro.valor_por_defecto::text AS valor_por_defecto,
                   parametro.ambito, parametro.pais_id, pais.codigo_iso AS pais_codigo,
                   parametro.vigente_desde, parametro.vigente_hasta,
                   parametro.actualizado_por_id, parametro.actualizado_en_utc
            """ + FROM;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<ParametroFila> mapper;

    public ParametroAdminRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = this::map;
    }

    public Page<ParametroFila> listar(
            String clave,
            String ambito,
            Set<String> paisesPermitidos,
            Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (clave != null && !clave.isBlank()) {
            where.append(" AND lower(parametro.clave) LIKE lower(?)");
            args.add("%" + clave.trim() + "%");
        }
        if (ambito != null && !ambito.isBlank()) {
            where.append(" AND parametro.ambito = ?");
            args.add(ambito.trim());
        }
        if (paisesPermitidos != null && !paisesPermitidos.isEmpty()) {
            List<String> paises = new TreeSet<>(paisesPermitidos).stream().toList();
            String placeholders = paises.stream()
                    .map(ignored -> "?")
                    .collect(Collectors.joining(","));
            where.append(" AND (parametro.ambito = 'NEGOCIO' OR pais.codigo_iso IN (")
                    .append(placeholders).append("))");
            args.addAll(paises);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) " + FROM + where,
                Long.class,
                args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());
        List<ParametroFila> filas = jdbc.query(
                COLUMNS + where + " ORDER BY parametro.clave, parametro.ambito,"
                        + " pais.codigo_iso NULLS FIRST, parametro.vigente_desde DESC"
                        + " LIMIT ? OFFSET ?",
                mapper,
                pageArgs.toArray());
        return new PageImpl<>(filas, pageable, total == null ? 0 : total);
    }

    public Optional<ParametroFila> buscar(UUID id) {
        return jdbc.query(
                        COLUMNS + " WHERE parametro.id = ?",
                        mapper,
                        id)
                .stream()
                .findFirst();
    }

    public Optional<ParametroFila> buscarSuperpuesto(
            String clave,
            String ambito,
            UUID paisId,
            LocalDate vigenteDesde,
            LocalDate vigenteHasta,
            UUID excluirId) {
        StringBuilder sql = new StringBuilder(COLUMNS).append(
                """
                 WHERE parametro.clave = ? AND parametro.ambito = ?
                   AND (parametro.vigente_desde <= ?)
                   AND (parametro.vigente_hasta IS NULL OR parametro.vigente_hasta >= ?)
                """);
        List<Object> args = new ArrayList<>();
        args.add(clave);
        args.add(ambito);
        args.add(vigenteHasta == null ? LocalDate.MAX : vigenteHasta);
        args.add(vigenteDesde);
        if (paisId == null) {
            sql.append(" AND parametro.pais_id IS NULL");
        } else {
            sql.append(" AND parametro.pais_id = ?");
            args.add(paisId);
        }
        if (excluirId != null) {
            sql.append(" AND parametro.id <> ?");
            args.add(excluirId);
        }
        return jdbc.query(sql.toString(), mapper, args.toArray())
                .stream()
                .findFirst();
    }

    public UUID crear(ParametroFila parametro, UUID actorId, OffsetDateTime ahora) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO public.parametro
                    (id, clave, valor, tipo, minimo, maximo, valor_por_defecto,
                     ambito, pais_id, vigente_desde, vigente_hasta,
                     actualizado_por_id, actualizado_en_utc)
                VALUES (?, ?, CAST(? AS jsonb), ?, ?, ?, CAST(? AS jsonb),
                        ?, ?, ?, ?, ?, ?)
                """,
                id,
                parametro.clave(),
                json(parametro.valor()),
                parametro.tipo(),
                parametro.minimo(),
                parametro.maximo(),
                json(parametro.valorPorDefecto()),
                parametro.ambito(),
                parametro.paisId(),
                parametro.vigenteDesde(),
                parametro.vigenteHasta(),
                actorId,
                ahora);
        return id;
    }

    public boolean actualizar(
            UUID id,
            ParametroFila cambios,
            UUID actorId,
            OffsetDateTime ahora) {
        return jdbc.update(
                """
                UPDATE public.parametro
                SET valor = CAST(? AS jsonb), minimo = ?, maximo = ?,
                    ambito = ?, pais_id = ?, vigente_desde = ?, vigente_hasta = ?,
                    actualizado_por_id = ?, actualizado_en_utc = ?
                WHERE id = ?
                """,
                json(cambios.valor()),
                cambios.minimo(),
                cambios.maximo(),
                cambios.ambito(),
                cambios.paisId(),
                cambios.vigenteDesde(),
                cambios.vigenteHasta(),
                actorId,
                ahora,
                id) == 1;
    }

    public Optional<String> codigoPais(UUID paisId) {
        return jdbc.queryForList(
                        "SELECT codigo_iso FROM public.pais WHERE id = ?",
                        String.class,
                        paisId)
                .stream()
                .findFirst();
    }

    private ParametroFila map(ResultSet rs, int rowNumber) throws SQLException {
        try {
            return new ParametroFila(
                    rs.getObject("id", UUID.class),
                    rs.getString("clave"),
                    objectMapper.readTree(rs.getString("valor")),
                    rs.getString("tipo"),
                    rs.getBigDecimal("minimo"),
                    rs.getBigDecimal("maximo"),
                    objectMapper.readTree(rs.getString("valor_por_defecto")),
                    rs.getString("ambito"),
                    rs.getObject("pais_id", UUID.class),
                    rs.getString("pais_codigo"),
                    rs.getObject("vigente_desde", LocalDate.class),
                    rs.getObject("vigente_hasta", LocalDate.class),
                    rs.getObject("actualizado_por_id", UUID.class),
                    rs.getObject("actualizado_en_utc", OffsetDateTime.class));
        } catch (JsonProcessingException exception) {
            throw new SQLException(
                    "No se pudo interpretar el JSON del parámetro.",
                    exception);
        }
    }

    private String json(JsonNode value) {
        return value == null ? null : value.toString();
    }
}
