package com.inventario.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.domain.Categoria;
import com.inventario.domain.DefinicionAtributo;
import com.inventario.domain.PrecioProducto;
import com.inventario.domain.Producto;
import com.inventario.domain.ProductoTraduccion;
import com.inventario.domain.TipoProducto;
import com.inventario.dto.ReferenciaOpcion;
import com.inventario.generated.CatalogoCrudSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogoRepository {

    private static final RowMapper<Categoria> CATEGORIA_ROW_MAPPER =
            (resultSet, rowNumber) -> mapCategoria(resultSet);
    private static final RowMapper<TipoProducto> TIPO_PRODUCTO_ROW_MAPPER =
            (resultSet, rowNumber) -> mapTipoProducto(resultSet);
    private static final RowMapper<ProductoTraduccion> TRADUCCION_ROW_MAPPER =
            (resultSet, rowNumber) -> mapTraduccion(resultSet);
    private static final RowMapper<PrecioProducto> PRECIO_ROW_MAPPER =
            (resultSet, rowNumber) -> mapPrecio(resultSet);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<DefinicionAtributo> DEFINICION_ROW_MAPPER;
    private final RowMapper<Producto> PRODUCTO_ROW_MAPPER;

    public CatalogoRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.DEFINICION_ROW_MAPPER = (resultSet, rowNumber) -> mapDefinicion(resultSet);
        this.PRODUCTO_ROW_MAPPER = (resultSet, rowNumber) -> mapProducto(resultSet);
    }

    public Page<Categoria> listarCategorias(String buscar, Pageable pageable) {
        return pagina(
                CatalogoCrudSql.CATEGORIA_COLUMNS,
                "public.categoria c",
                CATEGORIA_ROW_MAPPER,
                pageable,
                "c.codigo",
                buscar,
                "c.codigo",
                "c.nombre");
    }

    public Optional<Categoria> buscarCategoria(UUID id) {
        return buscarUno(CatalogoCrudSql.CATEGORIA_SELECT_BY_ID, CATEGORIA_ROW_MAPPER, id);
    }

    public UUID crearCategoria(Categoria categoria) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                CatalogoCrudSql.CATEGORIA_INSERT,
                id,
                categoria.codigo(),
                categoria.nombre(),
                categoria.padreId(),
                categoria.activo());
        return id;
    }

    public boolean actualizarCategoria(Categoria categoria) {
        return jdbcTemplate.update(
                CatalogoCrudSql.CATEGORIA_UPDATE,
                categoria.codigo(),
                categoria.nombre(),
                categoria.padreId(),
                categoria.activo(),
                categoria.id()) == 1;
    }

    public boolean desactivarCategoria(UUID id) {
        return jdbcTemplate.update(
                "UPDATE public.categoria SET activo = FALSE WHERE id = ?", id) == 1;
    }

    public void bloquearCategoria(UUID id) {
        jdbcTemplate.query(
                "SELECT id FROM public.categoria WHERE id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                id);
    }

    public boolean tieneCategoriasHijas(UUID id) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM public.categoria WHERE padre_id = ? AND activo)",
                Boolean.class,
                id);
        return Boolean.TRUE.equals(exists);
    }

    public boolean estaCategoriaEnProductoActivo(UUID id) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM public.producto
                    WHERE activo AND (categoria_id = ? OR subcategoria_id = ?)
                )
                """,
                Boolean.class,
                id,
                id);
        return Boolean.TRUE.equals(exists);
    }

    public Page<TipoProducto> listarTiposProducto(String buscar, Pageable pageable) {
        return pagina(
                CatalogoCrudSql.TIPO_PRODUCTO_COLUMNS,
                "public.tipo_producto tp",
                TIPO_PRODUCTO_ROW_MAPPER,
                pageable,
                "tp.codigo",
                buscar,
                "tp.codigo",
                "tp.nombre");
    }

    public Optional<TipoProducto> buscarTipoProducto(UUID id) {
        return buscarUno(
                CatalogoCrudSql.TIPO_PRODUCTO_SELECT_BY_ID,
                TIPO_PRODUCTO_ROW_MAPPER,
                id);
    }

    public UUID crearTipoProducto(TipoProducto tipoProducto) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                CatalogoCrudSql.TIPO_PRODUCTO_INSERT,
                id,
                tipoProducto.codigo(),
                tipoProducto.nombre(),
                tipoProducto.activo());
        return id;
    }

    public boolean actualizarTipoProducto(TipoProducto tipoProducto) {
        return jdbcTemplate.update(
                CatalogoCrudSql.TIPO_PRODUCTO_UPDATE,
                tipoProducto.codigo(),
                tipoProducto.nombre(),
                tipoProducto.activo(),
                tipoProducto.id()) == 1;
    }

    public boolean desactivarTipoProducto(UUID id) {
        return jdbcTemplate.update(
                "UPDATE public.tipo_producto SET activo = FALSE WHERE id = ?", id) == 1;
    }

    public void bloquearTipoProducto(UUID id) {
        jdbcTemplate.query(
                "SELECT id FROM public.tipo_producto WHERE id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                id);
    }

    public boolean tieneProductosActivos(UUID tipoProductoId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM public.producto WHERE tipo_producto_id = ? AND activo)",
                Boolean.class,
                tipoProductoId);
        return Boolean.TRUE.equals(exists);
    }

    public Page<DefinicionAtributo> listarDefiniciones(
            UUID tipoProductoId,
            String buscar,
            Pageable pageable) {
        String from = "public.atributo_definicion ad";
        List<Object> parameters = new ArrayList<>();
        String where = "";
        if (tipoProductoId != null) {
            where = " WHERE ad.tipo_producto_id = ?";
            parameters.add(tipoProductoId);
        }
        if (buscar != null && !buscar.isBlank()) {
            where += where.isEmpty() ? " WHERE " : " AND ";
            where += "lower(ad.nombre) LIKE lower(?)";
            parameters.add("%" + buscar.trim() + "%");
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + from + where,
                Long.class,
                parameters.toArray());
        parameters.add(pageable.getPageSize());
        parameters.add(pageable.getOffset());
        List<DefinicionAtributo> content = jdbcTemplate.query(
                "SELECT " + CatalogoCrudSql.ATRIBUTO_DEFINICION_COLUMNS
                        + " FROM " + from + where
                        + " ORDER BY ad.tipo_producto_id, ad.nombre LIMIT ? OFFSET ?",
                DEFINICION_ROW_MAPPER,
                parameters.toArray());
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    public List<DefinicionAtributo> listarDefinicionesPorTipo(UUID tipoProductoId) {
        return jdbcTemplate.query(
                CatalogoCrudSql.ATRIBUTO_DEFINICION_SELECT_ALL
                        + " WHERE tipo_producto_id = ? ORDER BY nombre",
                DEFINICION_ROW_MAPPER,
                tipoProductoId);
    }

    public Optional<DefinicionAtributo> buscarDefinicion(UUID id) {
        return buscarUno(
                CatalogoCrudSql.ATRIBUTO_DEFINICION_SELECT_BY_ID,
                DEFINICION_ROW_MAPPER,
                id);
    }

    public void bloquearDefinicion(UUID id) {
        jdbcTemplate.query(
                "SELECT id FROM public.atributo_definicion WHERE id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                id);
    }

    public UUID crearDefinicion(DefinicionAtributo definicion) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                CatalogoCrudSql.ATRIBUTO_DEFINICION_INSERT,
                id,
                definicion.tipoProductoId(),
                definicion.nombre(),
                definicion.tipoDato(),
                definicion.obligatorio(),
                jsonParameter(definicion.opciones()));
        return id;
    }

    public boolean actualizarDefinicion(DefinicionAtributo definicion) {
        return jdbcTemplate.update(
                CatalogoCrudSql.ATRIBUTO_DEFINICION_UPDATE,
                definicion.tipoProductoId(),
                definicion.nombre(),
                definicion.tipoDato(),
                definicion.obligatorio(),
                jsonParameter(definicion.opciones()),
                definicion.id()) == 1;
    }

    public Page<Producto> listarProductos(String buscar, Pageable pageable) {
        return pagina(
                CatalogoCrudSql.PRODUCTO_COLUMNS,
                "public.producto p",
                PRODUCTO_ROW_MAPPER,
                pageable,
                "p.codigo_interno",
                buscar,
                "p.codigo_interno");
    }

    public List<ReferenciaOpcion> listarReferenciasProducto(String idioma, int limite) {
        return jdbcTemplate.query(
                """
                SELECT p.id, p.codigo_interno,
                       COALESCE(
                           (SELECT pt.nombre
                            FROM public.producto_traduccion pt
                            JOIN public.idioma i ON i.id = pt.idioma_id
                            WHERE pt.producto_id = p.id AND lower(i.codigo_bcp47) = lower(?)),
                           (SELECT pt.nombre
                            FROM public.producto_traduccion pt
                            JOIN public.idioma i ON i.id = pt.idioma_id
                            WHERE pt.producto_id = p.id
                            ORDER BY i.codigo_bcp47
                            LIMIT 1),
                           p.codigo_interno) AS nombre
                FROM public.producto p
                WHERE p.activo
                ORDER BY nombre, p.codigo_interno
                LIMIT ?
                """,
                (rs, row) -> new ReferenciaOpcion(
                        rs.getObject("id", UUID.class),
                        rs.getString("codigo_interno"),
                        rs.getString("nombre")),
                idioma,
                limite);
    }

    public List<Producto> listarProductosActivosPorTipo(UUID tipoProductoId) {
        return jdbcTemplate.query(
                """
                SELECT id, codigo_interno, tipo_producto_id, categoria_id,
                       subcategoria_id, atributos, activo, creado_en_utc, actualizado_en_utc
                FROM public.producto
                WHERE tipo_producto_id = ? AND activo
                """,
                PRODUCTO_ROW_MAPPER,
                tipoProductoId);
    }

    public Optional<Producto> buscarProducto(UUID id) {
        return buscarUno(
                CatalogoCrudSql.PRODUCTO_SELECT_BY_ID,
                PRODUCTO_ROW_MAPPER,
                id);
    }

    public UUID crearProducto(Producto producto) {
        jdbcTemplate.update(
                CatalogoCrudSql.PRODUCTO_INSERT,
                producto.id(),
                producto.codigoInterno(),
                producto.tipoProductoId(),
                producto.categoriaId(),
                producto.subcategoriaId(),
                jsonParameter(producto.atributos()),
                producto.activo(),
                OffsetDateTime.ofInstant(producto.creadoEnUtc(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(producto.actualizadoEnUtc(), ZoneOffset.UTC));
        return producto.id();
    }

    public boolean actualizarProducto(Producto producto) {
        return jdbcTemplate.update(
                """
                UPDATE public.producto
                SET tipo_producto_id = ?, categoria_id = ?, subcategoria_id = ?,
                    atributos = CAST(? AS jsonb), actualizado_en_utc = ?
                WHERE id = ? AND activo
                """,
                producto.tipoProductoId(),
                producto.categoriaId(),
                producto.subcategoriaId(),
                producto.atributos().toString(),
                OffsetDateTime.ofInstant(producto.actualizadoEnUtc(), ZoneOffset.UTC),
                producto.id()) == 1;
    }

    public boolean desactivarProducto(UUID id, Instant actualizadoEnUtc) {
        return jdbcTemplate.update(
                """
                UPDATE public.producto SET activo = FALSE, actualizado_en_utc = ?
                WHERE id = ? AND activo
                """,
                OffsetDateTime.ofInstant(actualizadoEnUtc, ZoneOffset.UTC),
                id) == 1;
    }

    public void bloquearProducto(UUID id) {
        jdbcTemplate.queryForObject(
                "SELECT id FROM public.producto WHERE id = ? FOR UPDATE",
                UUID.class,
                id);
    }

    public List<ProductoTraduccion> listarTraducciones(UUID productoId) {
        return jdbcTemplate.query(
                """
                SELECT pt.id, pt.producto_id, pt.idioma_id, i.codigo_bcp47 AS idioma_codigo,
                       pt.nombre, pt.descripcion
                FROM public.producto_traduccion pt
                JOIN public.idioma i ON i.id = pt.idioma_id
                WHERE pt.producto_id = ?
                ORDER BY i.codigo_bcp47
                """,
                TRADUCCION_ROW_MAPPER,
                productoId);
    }

    public Optional<ProductoTraduccion> buscarTraduccion(UUID id, UUID productoId) {
        List<ProductoTraduccion> results = jdbcTemplate.query(
                """
                SELECT pt.id, pt.producto_id, pt.idioma_id, i.codigo_bcp47 AS idioma_codigo,
                       pt.nombre, pt.descripcion
                FROM public.producto_traduccion pt
                JOIN public.idioma i ON i.id = pt.idioma_id
                WHERE pt.id = ? AND pt.producto_id = ?
                """,
                TRADUCCION_ROW_MAPPER,
                id,
                productoId);
        return results.stream().findFirst();
    }

    public UUID crearTraduccion(ProductoTraduccion traduccion) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO public.producto_traduccion
                    (id, producto_id, idioma_id, nombre, descripcion)
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                traduccion.productoId(),
                traduccion.idiomaId(),
                traduccion.nombre(),
                traduccion.descripcion());
        return id;
    }

    public boolean actualizarTraduccion(ProductoTraduccion traduccion) {
        return jdbcTemplate.update(
                """
                UPDATE public.producto_traduccion SET nombre = ?, descripcion = ?
                WHERE id = ? AND producto_id = ?
                """,
                traduccion.nombre(),
                traduccion.descripcion(),
                traduccion.id(),
                traduccion.productoId()) == 1;
    }

    public boolean idiomaHabilitado(UUID idiomaId) {
        Boolean enabled = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM public.idioma WHERE id = ? AND habilitado
                )
                """,
                Boolean.class,
                idiomaId);
        return Boolean.TRUE.equals(enabled);
    }

    public List<PrecioProducto> listarPrecios(UUID productoId) {
        return jdbcTemplate.query(
                """
                SELECT pp.id, pp.producto_id, pp.moneda_id, m.codigo_iso AS moneda_codigo,
                       pp.importe, pp.vigente_desde, pp.vigente_hasta
                FROM public.precio_producto pp
                JOIN public.moneda m ON m.id = pp.moneda_id
                WHERE pp.producto_id = ?
                ORDER BY m.codigo_iso, pp.vigente_desde DESC
                """,
                PRECIO_ROW_MAPPER,
                productoId);
    }

    public UUID crearPrecio(PrecioProducto precio) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO public.precio_producto
                    (id, producto_id, moneda_id, importe, vigente_desde, vigente_hasta)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                precio.productoId(),
                precio.monedaId(),
                precio.importe(),
                precio.vigenteDesde(),
                precio.vigenteHasta());
        return id;
    }

    public boolean monedaExiste(UUID monedaId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM public.moneda WHERE id = ?)",
                Boolean.class,
                monedaId);
        return Boolean.TRUE.equals(exists);
    }

    public boolean precioSeSuperpone(
            UUID productoId,
            UUID monedaId,
            LocalDate vigenteDesde,
            LocalDate vigenteHasta) {
        Boolean overlaps = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM public.precio_producto
                    WHERE producto_id = ? AND moneda_id = ?
                      AND (vigente_hasta IS NULL OR vigente_hasta >= ?)
                      AND (?::date IS NULL OR vigente_desde <= ?)
                )
                """,
                Boolean.class,
                productoId,
                monedaId,
                vigenteDesde,
                vigenteHasta,
                vigenteHasta);
        return Boolean.TRUE.equals(overlaps);
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
        return jdbcTemplate.query(sql, rowMapper, id).stream().findFirst();
    }

    private SqlParameterValue jsonParameter(JsonNode value) {
        return value == null ? new SqlParameterValue(Types.OTHER, null)
                : new SqlParameterValue(Types.OTHER, value.toString());
    }

    private static Categoria mapCategoria(ResultSet resultSet) throws SQLException {
        return new Categoria(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("codigo"),
                resultSet.getString("nombre"),
                resultSet.getObject("padre_id", UUID.class),
                resultSet.getBoolean("activo"));
    }

    private static TipoProducto mapTipoProducto(ResultSet resultSet) throws SQLException {
        return new TipoProducto(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("codigo"),
                resultSet.getString("nombre"),
                resultSet.getBoolean("activo"));
    }

    private DefinicionAtributo mapDefinicion(ResultSet resultSet) throws SQLException {
        return new DefinicionAtributo(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tipo_producto_id", UUID.class),
                resultSet.getString("nombre"),
                resultSet.getString("tipo_dato"),
                resultSet.getBoolean("obligatorio"),
                parseJson(resultSet.getString("opciones")));
    }

    private Producto mapProducto(ResultSet resultSet) throws SQLException {
        return new Producto(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("codigo_interno"),
                resultSet.getObject("tipo_producto_id", UUID.class),
                resultSet.getObject("categoria_id", UUID.class),
                resultSet.getObject("subcategoria_id", UUID.class),
                parseJson(resultSet.getString("atributos")),
                resultSet.getBoolean("activo"),
                instant(resultSet.getObject("creado_en_utc", OffsetDateTime.class)),
                instant(resultSet.getObject("actualizado_en_utc", OffsetDateTime.class)));
    }

    private static ProductoTraduccion mapTraduccion(ResultSet resultSet) throws SQLException {
        return new ProductoTraduccion(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("producto_id", UUID.class),
                resultSet.getObject("idioma_id", UUID.class),
                resultSet.getString("idioma_codigo"),
                resultSet.getString("nombre"),
                resultSet.getString("descripcion"));
    }

    private static PrecioProducto mapPrecio(ResultSet resultSet) throws SQLException {
        return new PrecioProducto(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("producto_id", UUID.class),
                resultSet.getObject("moneda_id", UUID.class),
                resultSet.getString("moneda_codigo"),
                resultSet.getBigDecimal("importe"),
                resultSet.getObject("vigente_desde", LocalDate.class),
                resultSet.getObject("vigente_hasta", LocalDate.class));
    }

    private JsonNode parseJson(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo leer un valor JSON del catálogo.", exception);
        }
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
