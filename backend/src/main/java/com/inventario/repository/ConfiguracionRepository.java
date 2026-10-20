package com.inventario.repository;

import com.inventario.domain.Idioma;
import com.inventario.domain.Impuesto;
import com.inventario.domain.Moneda;
import com.inventario.domain.Pais;
import com.inventario.domain.TipoCambio;
import com.inventario.generated.ParametrizacionCrudSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ConfiguracionRepository {

    private static final RowMapper<Pais> PAIS_ROW_MAPPER =
            (resultSet, rowNumber) -> mapPais(resultSet);
    private static final RowMapper<Moneda> MONEDA_ROW_MAPPER =
            (resultSet, rowNumber) -> mapMoneda(resultSet);
    private static final RowMapper<Idioma> IDIOMA_ROW_MAPPER =
            (resultSet, rowNumber) -> mapIdioma(resultSet);
    private static final RowMapper<TipoCambio> TIPO_CAMBIO_ROW_MAPPER =
            (resultSet, rowNumber) -> mapTipoCambio(resultSet);
    private static final RowMapper<Impuesto> IMPUESTO_ROW_MAPPER =
            (resultSet, rowNumber) -> mapImpuesto(resultSet);

    private final JdbcTemplate jdbcTemplate;

    public ConfiguracionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<Pais> listarPaises(String buscar, Pageable pageable) {
        return pagina(
                ParametrizacionCrudSql.PAIS_COLUMNS,
                "public.pais p",
                PAIS_ROW_MAPPER,
                pageable,
                "p.codigo_iso",
                buscar,
                "p.codigo_iso",
                "p.nombre");
    }

    public Optional<Pais> buscarPais(UUID id) {
        return buscarUno(
                ParametrizacionCrudSql.PAIS_SELECT_BY_ID,
                PAIS_ROW_MAPPER,
                id);
    }

    public UUID crearPais(Pais pais) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                ParametrizacionCrudSql.PAIS_INSERT,
                id,
                pais.codigoIso(),
                pais.nombre(),
                pais.monedaLocalId(),
                pais.idiomaPredeterminadoId(),
                pais.zonaHoraria(),
                pais.habilitado());
        return id;
    }

    public boolean actualizarPais(Pais pais) {
        return jdbcTemplate.update(
                ParametrizacionCrudSql.PAIS_UPDATE,
                pais.codigoIso(),
                pais.nombre(),
                pais.monedaLocalId(),
                pais.idiomaPredeterminadoId(),
                pais.zonaHoraria(),
                pais.habilitado(),
                pais.id()) == 1;
    }

    public boolean eliminarPais(UUID id) {
        return jdbcTemplate.update(ParametrizacionCrudSql.PAIS_DELETE, id) == 1;
    }

    public Page<Moneda> listarMonedas(String buscar, Pageable pageable) {
        return pagina(
                ParametrizacionCrudSql.MONEDA_COLUMNS,
                "public.moneda m",
                MONEDA_ROW_MAPPER,
                pageable,
                "m.codigo_iso",
                buscar,
                "m.codigo_iso",
                "m.nombre");
    }

    public Optional<Moneda> buscarMoneda(UUID id) {
        return buscarUno(
                ParametrizacionCrudSql.MONEDA_SELECT_BY_ID,
                MONEDA_ROW_MAPPER,
                id);
    }

    public UUID crearMoneda(Moneda moneda) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                ParametrizacionCrudSql.MONEDA_INSERT,
                id,
                moneda.codigoIso(),
                moneda.nombre(),
                moneda.simbolo(),
                moneda.decimales(),
                moneda.formato(),
                moneda.reglaRedondeo());
        return id;
    }

    public boolean actualizarMoneda(Moneda moneda) {
        return jdbcTemplate.update(
                ParametrizacionCrudSql.MONEDA_UPDATE,
                moneda.codigoIso(),
                moneda.nombre(),
                moneda.simbolo(),
                moneda.decimales(),
                moneda.formato(),
                moneda.reglaRedondeo(),
                moneda.id()) == 1;
    }

    public boolean eliminarMoneda(UUID id) {
        return jdbcTemplate.update(ParametrizacionCrudSql.MONEDA_DELETE, id) == 1;
    }

    public Page<Idioma> listarIdiomas(String buscar, Pageable pageable) {
        return pagina(
                ParametrizacionCrudSql.IDIOMA_COLUMNS,
                "public.idioma i",
                IDIOMA_ROW_MAPPER,
                pageable,
                "i.codigo_bcp47",
                buscar,
                "i.codigo_bcp47",
                "i.nombre");
    }

    public Optional<Idioma> buscarIdioma(UUID id) {
        return buscarUno(
                ParametrizacionCrudSql.IDIOMA_SELECT_BY_ID,
                IDIOMA_ROW_MAPPER,
                id);
    }

    public UUID crearIdioma(Idioma idioma) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                ParametrizacionCrudSql.IDIOMA_INSERT,
                id,
                idioma.codigoBcp47(),
                idioma.nombre(),
                idioma.habilitado());
        return id;
    }

    public boolean actualizarIdioma(Idioma idioma) {
        return jdbcTemplate.update(
                ParametrizacionCrudSql.IDIOMA_UPDATE,
                idioma.codigoBcp47(),
                idioma.nombre(),
                idioma.habilitado(),
                idioma.id()) == 1;
    }

    public boolean eliminarIdioma(UUID id) {
        return jdbcTemplate.update(ParametrizacionCrudSql.IDIOMA_DELETE, id) == 1;
    }

    public Page<TipoCambio> listarTiposCambio(String buscar, Pageable pageable) {
        return pagina(
                "tc.id, tc.moneda_origen_id, mo.codigo_iso AS moneda_origen_codigo, "
                        + "tc.moneda_destino_id, md.codigo_iso AS moneda_destino_codigo, "
                        + "tc.tasa, tc.vigente_desde_utc, tc.origen, tc.actualizado_por_id",
                "public.tipo_cambio tc "
                        + "JOIN public.moneda mo ON mo.id = tc.moneda_origen_id "
                        + "JOIN public.moneda md ON md.id = tc.moneda_destino_id",
                TIPO_CAMBIO_ROW_MAPPER,
                pageable,
                "tc.vigente_desde_utc DESC, tc.id",
                buscar,
                "mo.codigo_iso",
                "md.codigo_iso");
    }

    public Optional<TipoCambio> buscarTipoCambio(UUID id) {
        return buscarUno(
                """
                SELECT tc.id, tc.moneda_origen_id, mo.codigo_iso AS moneda_origen_codigo,
                       tc.moneda_destino_id, md.codigo_iso AS moneda_destino_codigo,
                       tc.tasa, tc.vigente_desde_utc, tc.origen, tc.actualizado_por_id
                FROM public.tipo_cambio tc
                JOIN public.moneda mo ON mo.id = tc.moneda_origen_id
                JOIN public.moneda md ON md.id = tc.moneda_destino_id
                WHERE tc.id = ?
                """,
                TIPO_CAMBIO_ROW_MAPPER,
                id);
    }

    public Optional<TipoCambio> buscarTipoCambioVigente(
            UUID monedaOrigenId,
            UUID monedaDestinoId,
            Instant fecha) {
        List<TipoCambio> rates = jdbcTemplate.query(
                """
                SELECT tc.id, tc.moneda_origen_id, mo.codigo_iso AS moneda_origen_codigo,
                       tc.moneda_destino_id, md.codigo_iso AS moneda_destino_codigo,
                       tc.tasa, tc.vigente_desde_utc, tc.origen, tc.actualizado_por_id
                FROM public.tipo_cambio tc
                JOIN public.moneda mo ON mo.id = tc.moneda_origen_id
                JOIN public.moneda md ON md.id = tc.moneda_destino_id
                WHERE tc.moneda_origen_id = ? AND tc.moneda_destino_id = ?
                  AND tc.vigente_desde_utc <= ?
                ORDER BY tc.vigente_desde_utc DESC
                LIMIT 1
                """,
                TIPO_CAMBIO_ROW_MAPPER,
                monedaOrigenId,
                monedaDestinoId,
                OffsetDateTime.ofInstant(fecha, ZoneOffset.UTC));
        return rates.stream().findFirst();
    }

    public UUID crearTipoCambio(TipoCambio tipoCambio) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                ParametrizacionCrudSql.TIPO_CAMBIO_INSERT,
                id,
                tipoCambio.monedaOrigenId(),
                tipoCambio.monedaDestinoId(),
                tipoCambio.tasa(),
                OffsetDateTime.ofInstant(tipoCambio.vigenteDesdeUtc(), ZoneOffset.UTC),
                tipoCambio.origen(),
                tipoCambio.actualizadoPorId());
        return id;
    }

    public boolean actualizarTipoCambio(TipoCambio tipoCambio) {
        return jdbcTemplate.update(
                ParametrizacionCrudSql.TIPO_CAMBIO_UPDATE,
                tipoCambio.monedaOrigenId(),
                tipoCambio.monedaDestinoId(),
                tipoCambio.tasa(),
                OffsetDateTime.ofInstant(tipoCambio.vigenteDesdeUtc(), ZoneOffset.UTC),
                tipoCambio.origen(),
                tipoCambio.actualizadoPorId(),
                tipoCambio.id()) == 1;
    }

    public boolean eliminarTipoCambio(UUID id) {
        return jdbcTemplate.update(ParametrizacionCrudSql.TIPO_CAMBIO_DELETE, id) == 1;
    }

    public Page<Impuesto> listarImpuestos(String buscar, Pageable pageable) {
        return pagina(
                ParametrizacionCrudSql.IMPUESTO_COLUMNS,
                "public.impuesto im",
                IMPUESTO_ROW_MAPPER,
                pageable,
                "im.vigente_desde DESC, im.id",
                buscar,
                "im.nombre");
    }

    public Optional<Impuesto> buscarImpuesto(UUID id) {
        return buscarUno(
                ParametrizacionCrudSql.IMPUESTO_SELECT_BY_ID,
                IMPUESTO_ROW_MAPPER,
                id);
    }

    public Optional<Impuesto> buscarImpuestoVigente(
            UUID paisId,
            UUID categoriaId,
            LocalDate fecha) {
        List<Impuesto> taxes = jdbcTemplate.query(
                """
                SELECT id, pais_id, categoria_id, nombre, tasa, incluido_en_precio,
                       vigente_desde, vigente_hasta
                FROM public.impuesto
                WHERE pais_id = ?
                  AND (categoria_id IS NULL OR categoria_id = CAST(? AS uuid))
                  AND vigente_desde <= ?
                  AND (vigente_hasta IS NULL OR vigente_hasta >= ?)
                ORDER BY CASE WHEN categoria_id IS NULL THEN 1 ELSE 0 END,
                         vigente_desde DESC
                LIMIT 1
                """,
                IMPUESTO_ROW_MAPPER,
                paisId,
                categoriaId,
                fecha,
                fecha);
        return taxes.stream().findFirst();
    }

    public boolean existeImpuestoVigente(UUID paisId, LocalDate fecha) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM public.impuesto
                    WHERE pais_id = ? AND vigente_desde <= ?
                      AND (vigente_hasta IS NULL OR vigente_hasta >= ?)
                )
                """,
                Boolean.class,
                paisId,
                fecha,
                fecha);
        return Boolean.TRUE.equals(exists);
    }

    public boolean existeSolapamientoImpuesto(Impuesto impuesto) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM public.impuesto
                    WHERE pais_id = ?
                      AND categoria_id IS NOT DISTINCT FROM CAST(? AS uuid)
                      AND (CAST(? AS uuid) IS NULL OR id <> CAST(? AS uuid))
                      AND (CAST(? AS date) IS NULL OR vigente_desde <= CAST(? AS date))
                      AND (vigente_hasta IS NULL OR vigente_hasta >= ?)
                )
                """,
                Boolean.class,
                impuesto.paisId(),
                impuesto.categoriaId(),
                impuesto.id(),
                impuesto.id(),
                impuesto.vigenteHasta(),
                impuesto.vigenteHasta(),
                impuesto.vigenteDesde());
        return Boolean.TRUE.equals(exists);
    }

    public UUID crearImpuesto(Impuesto impuesto) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                ParametrizacionCrudSql.IMPUESTO_INSERT,
                id,
                impuesto.paisId(),
                impuesto.categoriaId(),
                impuesto.nombre(),
                impuesto.tasa(),
                impuesto.incluidoEnPrecio(),
                impuesto.vigenteDesde(),
                impuesto.vigenteHasta());
        return id;
    }

    public boolean actualizarImpuesto(Impuesto impuesto) {
        return jdbcTemplate.update(
                ParametrizacionCrudSql.IMPUESTO_UPDATE,
                impuesto.paisId(),
                impuesto.categoriaId(),
                impuesto.nombre(),
                impuesto.tasa(),
                impuesto.incluidoEnPrecio(),
                impuesto.vigenteDesde(),
                impuesto.vigenteHasta(),
                impuesto.id()) == 1;
    }

    public boolean eliminarImpuesto(UUID id) {
        return jdbcTemplate.update(ParametrizacionCrudSql.IMPUESTO_DELETE, id) == 1;
    }

    private <T> Page<T> pagina(
            String columns,
            String from,
            RowMapper<T> rowMapper,
            Pageable pageable,
            String orderBy,
            String buscar,
            String... searchColumns) {
        List<Object> parameters = new ArrayList<>();
        String where = "";
        if (buscar != null && !buscar.isBlank()) {
            where = " WHERE ("
                    + String.join(" OR ", java.util.Arrays.stream(searchColumns)
                            .map(column -> "lower(" + column + ") LIKE lower(?)")
                            .toList())
                    + ")";
            String term = "%" + buscar.trim() + "%";
            for (int index = 0; index < searchColumns.length; index++) {
                parameters.add(term);
            }
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + from + where,
                Long.class,
                parameters.toArray());
        parameters.add(pageable.getPageSize());
        parameters.add(pageable.getOffset());
        List<T> content = jdbcTemplate.query(
                "SELECT " + columns + " FROM " + from + where
                        + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                rowMapper,
                parameters.toArray());
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private <T> Optional<T> buscarUno(String sql, RowMapper<T> rowMapper, UUID id) {
        List<T> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.stream().findFirst();
    }

    private static Pais mapPais(ResultSet resultSet) throws SQLException {
        return new Pais(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("codigo_iso"),
                resultSet.getString("nombre"),
                resultSet.getObject("moneda_local_id", UUID.class),
                resultSet.getObject("idioma_predeterminado_id", UUID.class),
                resultSet.getString("zona_horaria"),
                resultSet.getBoolean("habilitado"));
    }

    private static Moneda mapMoneda(ResultSet resultSet) throws SQLException {
        return new Moneda(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("codigo_iso"),
                resultSet.getString("nombre"),
                resultSet.getString("simbolo"),
                resultSet.getInt("decimales"),
                resultSet.getString("formato"),
                resultSet.getString("regla_redondeo"));
    }

    private static Idioma mapIdioma(ResultSet resultSet) throws SQLException {
        return new Idioma(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("codigo_bcp47"),
                resultSet.getString("nombre"),
                resultSet.getBoolean("habilitado"));
    }

    private static TipoCambio mapTipoCambio(ResultSet resultSet) throws SQLException {
        OffsetDateTime effectiveFrom =
                resultSet.getObject("vigente_desde_utc", OffsetDateTime.class);
        return new TipoCambio(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("moneda_origen_id", UUID.class),
                resultSet.getString("moneda_origen_codigo"),
                resultSet.getObject("moneda_destino_id", UUID.class),
                resultSet.getString("moneda_destino_codigo"),
                resultSet.getBigDecimal("tasa"),
                effectiveFrom == null ? null : effectiveFrom.toInstant(),
                resultSet.getString("origen"),
                resultSet.getObject("actualizado_por_id", UUID.class));
    }

    private static Impuesto mapImpuesto(ResultSet resultSet) throws SQLException {
        return new Impuesto(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("pais_id", UUID.class),
                resultSet.getObject("categoria_id", UUID.class),
                resultSet.getString("nombre"),
                resultSet.getBigDecimal("tasa"),
                resultSet.getBoolean("incluido_en_precio"),
                resultSet.getObject("vigente_desde", LocalDate.class),
                resultSet.getObject("vigente_hasta", LocalDate.class));
    }
}
