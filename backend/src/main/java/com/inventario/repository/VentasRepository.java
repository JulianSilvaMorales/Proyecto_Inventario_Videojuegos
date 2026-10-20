package com.inventario.repository;

import com.inventario.domain.ProductoVenta;
import com.inventario.domain.Venta;
import com.inventario.domain.VentaLinea;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class VentasRepository {

    private static final RowMapper<ProductoVenta> PRODUCTO_MAPPER =
            (rs, row) -> new ProductoVenta(
                    rs.getObject("id", UUID.class),
                    rs.getString("codigo_interno"),
                    rs.getObject("categoria_id", UUID.class),
                    rs.getBoolean("activo"));

    private static final RowMapper<Venta> VENTA_MAPPER = VentasRepository::mapVenta;
    private static final RowMapper<VentaLinea> LINEA_MAPPER = VentasRepository::mapLinea;

    private final JdbcTemplate jdbc;

    public VentasRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProductoVenta> buscarProducto(UUID id) {
        return jdbc.query(
                        """
                        SELECT id, codigo_interno, categoria_id, activo
                        FROM public.producto
                        WHERE id = ?
                        """,
                        PRODUCTO_MAPPER,
                        id)
                .stream()
                .findFirst();
    }

    public boolean bloquearProducto(UUID id) {
        return !jdbc.query(
                        "SELECT id FROM public.producto WHERE id = ? FOR UPDATE",
                        (rs, row) -> rs.getObject("id", UUID.class),
                        id)
                .isEmpty();
    }

    public Optional<BigDecimal> buscarPrecioVigente(
            UUID productoId,
            UUID monedaId,
            LocalDate fecha) {
        return jdbc.query(
                        """
                        SELECT importe
                        FROM public.precio_producto
                        WHERE producto_id = ? AND moneda_id = ?
                          AND vigente_desde <= ?
                          AND (vigente_hasta IS NULL OR vigente_hasta >= ?)
                        ORDER BY vigente_desde DESC
                        LIMIT 1
                        """,
                        (rs, row) -> rs.getBigDecimal("importe"),
                        productoId,
                        monedaId,
                        fecha,
                        fecha)
                .stream()
                .findFirst();
    }

    public boolean clienteActivoEnPais(UUID clienteId, UUID paisId) {
        Boolean active = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM public.cliente
                    WHERE id = ? AND pais_id = ? AND activo
                )
                """,
                Boolean.class,
                clienteId,
                paisId);
        return Boolean.TRUE.equals(active);
    }

    public void crearVenta(Venta venta, List<VentaLinea> lineas) {
        jdbc.update(
                """
                INSERT INTO public.venta
                    (id, numero, cliente_id, pais_id, moneda_id, tipo_cambio_aplicado,
                     subtotal, total_impuestos, total, total_moneda_base, estado,
                     estado_pago, medio_pago, registrada_por_id, creada_en_utc,
                     pagada_en_utc, confirmada_en_utc, anulada_en_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                venta.id(),
                venta.numero(),
                venta.clienteId(),
                venta.paisId(),
                venta.monedaId(),
                venta.tipoCambioAplicado(),
                venta.subtotal(),
                venta.totalImpuestos(),
                venta.total(),
                venta.totalMonedaBase(),
                venta.estado(),
                venta.estadoPago(),
                venta.medioPago(),
                venta.registradaPorId(),
                venta.creadaEnUtc(),
                venta.pagadaEnUtc(),
                venta.confirmadaEnUtc(),
                venta.anuladaEnUtc());

        for (VentaLinea linea : lineas) {
            jdbc.update(
                    """
                    INSERT INTO public.venta_linea
                        (id, venta_id, producto_id, bodega_id, cantidad, precio_unitario,
                         impuesto_id, nombre_impuesto_aplicado, tasa_impuesto_aplicada,
                         importe_impuesto, subtotal_linea, total_linea)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    linea.id(),
                    linea.ventaId(),
                    linea.productoId(),
                    linea.bodegaId(),
                    linea.cantidad(),
                    linea.precioUnitario(),
                    linea.impuestoId(),
                    linea.nombreImpuestoAplicado(),
                    linea.tasaImpuestoAplicada(),
                    linea.importeImpuesto(),
                    linea.subtotalLinea(),
                    linea.totalLinea());
        }
    }

    public Optional<Venta> buscarVenta(UUID id) {
        return jdbc.query(
                        """
                        SELECT v.id, v.numero, v.cliente_id, v.pais_id,
                               p.codigo_iso AS pais_codigo, v.moneda_id,
                               m.codigo_iso AS moneda_codigo, v.tipo_cambio_aplicado,
                               v.subtotal, v.total_impuestos, v.total,
                               v.total_moneda_base, v.estado, v.estado_pago,
                               v.medio_pago, v.registrada_por_id, v.creada_en_utc,
                               v.pagada_en_utc, v.confirmada_en_utc, v.anulada_en_utc
                        FROM public.venta v
                        JOIN public.pais p ON p.id = v.pais_id
                        JOIN public.moneda m ON m.id = v.moneda_id
                        WHERE v.id = ?
                        """,
                        VENTA_MAPPER,
                        id)
                .stream()
                .findFirst();
    }

    public List<VentaLinea> listarLineas(UUID ventaId) {
        return jdbc.query(
                """
                SELECT vl.id, vl.venta_id, vl.producto_id,
                       p.codigo_interno AS codigo_producto,
                       COALESCE(pt.nombre, p.codigo_interno) AS nombre_producto,
                       vl.bodega_id, b.nombre AS bodega_nombre, vl.cantidad,
                       vl.precio_unitario, vl.impuesto_id,
                       vl.nombre_impuesto_aplicado, vl.tasa_impuesto_aplicada,
                       vl.importe_impuesto, vl.subtotal_linea, vl.total_linea
                FROM public.venta_linea vl
                JOIN public.producto p ON p.id = vl.producto_id
                JOIN public.bodega b ON b.id = vl.bodega_id
                LEFT JOIN LATERAL (
                    SELECT traduccion.nombre
                    FROM public.producto_traduccion traduccion
                    JOIN public.idioma idioma ON idioma.id = traduccion.idioma_id
                    WHERE traduccion.producto_id = p.id
                      AND lower(idioma.codigo_bcp47) LIKE 'es%'
                    ORDER BY CASE WHEN lower(idioma.codigo_bcp47) = 'es' THEN 0 ELSE 1 END
                    LIMIT 1
                ) pt ON TRUE
                WHERE vl.venta_id = ?
                ORDER BY p.codigo_interno, b.codigo
                """,
                LINEA_MAPPER,
                ventaId);
    }

    public boolean bloquearVenta(UUID id) {
        return !jdbc.query(
                        "SELECT id FROM public.venta WHERE id = ? FOR UPDATE",
                        (rs, row) -> rs.getObject("id", UUID.class),
                        id)
                .isEmpty();
    }

    public boolean confirmarVenta(UUID id, String medioPago, OffsetDateTime ahora) {
        return jdbc.update(
                """
                UPDATE public.venta
                SET estado = 'CONFIRMADA', estado_pago = 'PAGADO', medio_pago = ?,
                    pagada_en_utc = ?, confirmada_en_utc = ?
                WHERE id = ? AND estado = 'PENDIENTE_DE_PAGO'
                  AND estado_pago = 'SIN_PAGO'
                """,
                medioPago,
                ahora,
                ahora,
                id) == 1;
    }

    public boolean anularVenta(UUID id, OffsetDateTime ahora) {
        return jdbc.update(
                """
                UPDATE public.venta
                SET estado = 'ANULADA', anulada_en_utc = ?
                WHERE id = ? AND estado = 'PENDIENTE_DE_PAGO'
                  AND estado_pago = 'SIN_PAGO'
                """,
                ahora,
                id) == 1;
    }

    private static Venta mapVenta(ResultSet rs, int row) throws SQLException {
        return new Venta(
                rs.getObject("id", UUID.class),
                rs.getString("numero"),
                rs.getObject("cliente_id", UUID.class),
                rs.getObject("pais_id", UUID.class),
                rs.getString("pais_codigo"),
                rs.getObject("moneda_id", UUID.class),
                rs.getString("moneda_codigo"),
                rs.getBigDecimal("tipo_cambio_aplicado"),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("total_impuestos"),
                rs.getBigDecimal("total"),
                rs.getBigDecimal("total_moneda_base"),
                rs.getString("estado"),
                rs.getString("estado_pago"),
                rs.getString("medio_pago"),
                rs.getObject("registrada_por_id", UUID.class),
                rs.getObject("creada_en_utc", OffsetDateTime.class),
                rs.getObject("pagada_en_utc", OffsetDateTime.class),
                rs.getObject("confirmada_en_utc", OffsetDateTime.class),
                rs.getObject("anulada_en_utc", OffsetDateTime.class));
    }

    private static VentaLinea mapLinea(ResultSet rs, int row) throws SQLException {
        return new VentaLinea(
                rs.getObject("id", UUID.class),
                rs.getObject("venta_id", UUID.class),
                rs.getObject("producto_id", UUID.class),
                rs.getString("codigo_producto"),
                rs.getString("nombre_producto"),
                rs.getObject("bodega_id", UUID.class),
                rs.getString("bodega_nombre"),
                rs.getInt("cantidad"),
                rs.getBigDecimal("precio_unitario"),
                rs.getObject("impuesto_id", UUID.class),
                rs.getString("nombre_impuesto_aplicado"),
                rs.getBigDecimal("tasa_impuesto_aplicada"),
                rs.getBigDecimal("importe_impuesto"),
                rs.getBigDecimal("subtotal_linea"),
                rs.getBigDecimal("total_linea"));
    }
}
