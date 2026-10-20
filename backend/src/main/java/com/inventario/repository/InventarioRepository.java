package com.inventario.repository;

import com.inventario.domain.AjusteInventario;
import com.inventario.domain.Bodega;
import com.inventario.domain.EntradaInventario;
import com.inventario.domain.EntradaInventarioLinea;
import com.inventario.domain.LineaRecepcion;
import com.inventario.domain.MovimientoInventario;
import com.inventario.domain.OrdenCompra;
import com.inventario.domain.OrdenCompraLinea;
import com.inventario.domain.Proveedor;
import com.inventario.domain.SaldoInventario;
import com.inventario.generated.InventarioCrudSql;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InventarioRepository {

    private static final RowMapper<Bodega> BODEGA_MAPPER =
            (rs, row) -> new Bodega(
                    uuid(rs, "id"),
                    rs.getString("codigo"),
                    rs.getString("nombre"),
                    uuid(rs, "categoria_id"),
                    uuid(rs, "pais_id"),
                    rs.getBoolean("activo"));
    private static final RowMapper<Proveedor> PROVEEDOR_MAPPER =
            (rs, row) -> new Proveedor(
                    uuid(rs, "id"),
                    rs.getString("razon_social"),
                    uuid(rs, "pais_id"),
                    rs.getString("identificacion_tributaria"),
                    rs.getString("persona_contacto"),
                    rs.getString("correo"),
                    rs.getString("telefono"),
                    uuid(rs, "moneda_compra_id"),
                    rs.getString("condiciones_pago"),
                    rs.getBoolean("activo"));
    private static final RowMapper<OrdenCompra> ORDEN_MAPPER =
            (rs, row) -> new OrdenCompra(
                    uuid(rs, "id"),
                    rs.getString("numero"),
                    uuid(rs, "proveedor_id"),
                    rs.getString("proveedor_nombre"),
                    uuid(rs, "pais_id"),
                    uuid(rs, "moneda_id"),
                    rs.getBigDecimal("tipo_cambio"),
                    rs.getString("estado"),
                    uuid(rs, "creada_por_id"),
                    rs.getObject("creada_en_utc", OffsetDateTime.class));
    private static final RowMapper<OrdenCompraLinea> ORDEN_LINEA_MAPPER =
            (rs, row) -> new OrdenCompraLinea(
                    uuid(rs, "id"),
                    uuid(rs, "producto_id"),
                    rs.getString("codigo_producto"),
                    rs.getString("nombre_producto"),
                    rs.getInt("cantidad_pedida"),
                    rs.getInt("cantidad_recibida"),
                    rs.getBigDecimal("costo_unitario"));
    private static final RowMapper<EntradaInventario> ENTRADA_MAPPER =
            (rs, row) -> new EntradaInventario(
                    uuid(rs, "id"),
                    uuid(rs, "orden_compra_id"),
                    rs.getString("numero_orden"),
                    uuid(rs, "bodega_id"),
                    rs.getString("numero_documento"),
                    rs.getBigDecimal("tipo_cambio_aplicado"),
                    uuid(rs, "recibida_por_id"),
                    rs.getObject("recibida_en_utc", OffsetDateTime.class));
    private static final RowMapper<EntradaInventarioLinea> ENTRADA_LINEA_MAPPER =
            (rs, row) -> new EntradaInventarioLinea(
                    uuid(rs, "id"),
                    uuid(rs, "orden_compra_linea_id"),
                    uuid(rs, "producto_id"),
                    rs.getString("codigo_producto"),
                    rs.getInt("cantidad"),
                    rs.getBigDecimal("costo_unitario"),
                    rs.getBigDecimal("costo_unitario_moneda_base"));
    private static final RowMapper<SaldoInventario> SALDO_MAPPER =
            (rs, row) -> new SaldoInventario(
                    uuid(rs, "producto_id"),
                    rs.getString("codigo_producto"),
                    rs.getString("nombre_producto"),
                    uuid(rs, "bodega_id"),
                    rs.getString("bodega_codigo"),
                    rs.getString("bodega_nombre"),
                    rs.getInt("cantidad"),
                    rs.getObject("actualizado_en_utc", OffsetDateTime.class));
    private static final RowMapper<MovimientoInventario> MOVIMIENTO_MAPPER =
            (rs, row) -> new MovimientoInventario(
                    uuid(rs, "id"),
                    uuid(rs, "producto_id"),
                    rs.getString("codigo_producto"),
                    uuid(rs, "bodega_id"),
                    rs.getString("bodega_nombre"),
                    rs.getString("tipo"),
                    rs.getInt("cantidad"),
                    rs.getString("referencia_tipo"),
                    uuid(rs, "referencia_id"),
                    uuid(rs, "usuario_id"),
                    rs.getObject("fecha_utc", OffsetDateTime.class),
                    rs.getString("observacion"));
    private static final RowMapper<AjusteInventario> AJUSTE_MAPPER =
            (rs, row) -> new AjusteInventario(
                    uuid(rs, "id"),
                    uuid(rs, "producto_id"),
                    rs.getString("codigo_producto"),
                    uuid(rs, "bodega_id"),
                    rs.getString("bodega_nombre"),
                    rs.getString("tipo"),
                    rs.getInt("cantidad_sistema"),
                    rs.getInt("cantidad_contada"),
                    rs.getInt("diferencia"),
                    rs.getString("motivo"),
                    rs.getString("estado"),
                    uuid(rs, "solicitado_por_id"),
                    uuid(rs, "aprobado_por_id"),
                    rs.getObject("solicitado_en_utc", OffsetDateTime.class),
                    rs.getObject("aprobado_en_utc", OffsetDateTime.class),
                    rs.getObject("aplicado_en_utc", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public InventarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<Bodega> listarBodegas(String buscar, Pageable pageable) {
        return listarBodegas(buscar, Set.of(), pageable);
    }

    public Page<Bodega> listarBodegas(
            String buscar,
            Set<String> paisesPermitidos,
            Pageable pageable) {
        StringBuilder from = new StringBuilder("FROM public.bodega b WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (buscar != null && !buscar.isBlank()) {
            from.append(" AND (lower(b.codigo) LIKE lower(?) OR lower(b.nombre) LIKE lower(?))");
            args.add("%" + buscar.trim() + "%");
            args.add("%" + buscar.trim() + "%");
        }
        agregarFiltroPaises(from, args, paisesPermitidos);
        return pagina(
                "b.id, b.codigo, b.nombre, b.categoria_id, b.pais_id, b.activo",
                from.toString(),
                "b.codigo",
                BODEGA_MAPPER,
                pageable,
                args);
    }

    public Optional<Bodega> buscarBodega(UUID id) {
        return uno(
                InventarioCrudSql.BODEGA_SELECT_BY_ID,
                BODEGA_MAPPER,
                id);
    }

    public UUID crearBodega(Bodega bodega) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                InventarioCrudSql.BODEGA_INSERT,
                id,
                bodega.codigo(),
                bodega.nombre(),
                bodega.categoriaId(),
                bodega.paisId(),
                bodega.activo());
        return id;
    }

    public boolean actualizarBodega(Bodega bodega) {
        return jdbc.update(
                InventarioCrudSql.BODEGA_UPDATE,
                bodega.codigo(),
                bodega.nombre(),
                bodega.categoriaId(),
                bodega.paisId(),
                bodega.activo(),
                bodega.id()) == 1;
    }

    public void bloquearBodega(UUID id) {
        jdbc.query(
                "SELECT id FROM public.bodega WHERE id = ? FOR UPDATE",
                (rs, row) -> uuid(rs, "id"),
                id);
    }

    public boolean bodegaConSaldoPositivo(UUID id) {
        return existe(
                "SELECT EXISTS (SELECT 1 FROM public.saldo_inventario"
                        + " WHERE bodega_id = ? AND cantidad > 0)",
                id);
    }

    public boolean bodegaConAjustesAbiertos(UUID id) {
        return existe(
                """
                SELECT EXISTS (
                    SELECT 1 FROM public.ajuste_inventario
                    WHERE bodega_id = ? AND estado IN ('PENDIENTE_DE_APROBACION', 'APROBADO')
                )
                """,
                id);
    }

    public boolean categoriaActiva(UUID id) {
        return existe(
                "SELECT EXISTS (SELECT 1 FROM public.categoria WHERE id = ? AND activo)",
                id);
    }

    public Page<Proveedor> listarProveedores(
            String buscar,
            Set<String> paisesPermitidos,
            Pageable pageable) {
        StringBuilder from = new StringBuilder("FROM public.proveedor p WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (buscar != null && !buscar.isBlank()) {
            from.append(" AND lower(p.razon_social) LIKE lower(?)");
            args.add("%" + buscar.trim() + "%");
        }
        agregarFiltroPaises(from, args, paisesPermitidos, "p.pais_id");
        return pagina(
                "p.id, p.razon_social, p.pais_id, p.identificacion_tributaria,"
                        + " p.persona_contacto, p.correo, p.telefono, p.moneda_compra_id,"
                        + " p.condiciones_pago, p.activo",
                from.toString(),
                "p.razon_social",
                PROVEEDOR_MAPPER,
                pageable,
                args);
    }

    public Optional<Proveedor> buscarProveedor(UUID id) {
        return uno(
                """
                SELECT id, razon_social, pais_id, identificacion_tributaria, persona_contacto,
                       correo, telefono, moneda_compra_id, condiciones_pago, activo
                FROM public.proveedor WHERE id = ?
                """,
                PROVEEDOR_MAPPER,
                id);
    }

    public UUID crearProveedor(Proveedor proveedor) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO public.proveedor
                    (id, razon_social, pais_id, identificacion_tributaria, persona_contacto,
                     correo, telefono, moneda_compra_id, condiciones_pago, activo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                """,
                id,
                proveedor.razonSocial(),
                proveedor.paisId(),
                proveedor.identificacionTributaria(),
                proveedor.personaContacto(),
                proveedor.correo(),
                proveedor.telefono(),
                proveedor.monedaCompraId(),
                proveedor.condicionesPago());
        return id;
    }

    public boolean actualizarProveedor(Proveedor proveedor) {
        return jdbc.update(
                """
                UPDATE public.proveedor
                SET razon_social = ?, pais_id = ?, identificacion_tributaria = ?,
                    persona_contacto = ?, correo = ?, telefono = ?, moneda_compra_id = ?,
                    condiciones_pago = ?, activo = ?
                WHERE id = ?
                """,
                proveedor.razonSocial(),
                proveedor.paisId(),
                proveedor.identificacionTributaria(),
                proveedor.personaContacto(),
                proveedor.correo(),
                proveedor.telefono(),
                proveedor.monedaCompraId(),
                proveedor.condicionesPago(),
                proveedor.activo(),
                proveedor.id()) == 1;
    }

    public void bloquearProveedor(UUID id) {
        jdbc.query(
                "SELECT id FROM public.proveedor WHERE id = ? FOR UPDATE",
                (rs, row) -> uuid(rs, "id"),
                id);
    }

    public boolean paisExiste(UUID id) {
        return existe("SELECT EXISTS (SELECT 1 FROM public.pais WHERE id = ?)", id);
    }

    public boolean monedaExiste(UUID id) {
        return existe("SELECT EXISTS (SELECT 1 FROM public.moneda WHERE id = ?)", id);
    }

    public String codigoMoneda(UUID id) {
        return jdbc.query(
                        "SELECT codigo_iso FROM public.moneda WHERE id = ?",
                        (rs, row) -> rs.getString("codigo_iso"),
                        id)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public Integer decimalesMoneda(String codigoIso) {
        return jdbc.query(
                        "SELECT decimales FROM public.moneda WHERE codigo_iso = ?",
                        (rs, row) -> rs.getInt("decimales"),
                        codigoIso)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public boolean productoActivo(UUID id) {
        return existe(
                "SELECT EXISTS (SELECT 1 FROM public.producto WHERE id = ? AND activo)",
                id);
    }

    public boolean productoExiste(UUID id) {
        return existe("SELECT EXISTS (SELECT 1 FROM public.producto WHERE id = ?)", id);
    }

    public boolean proveedorProducto(UUID proveedorId, UUID productoId) {
        Boolean result = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM public.producto_proveedor pp
                    JOIN public.proveedor p ON p.id = pp.proveedor_id
                    WHERE pp.proveedor_id = ? AND pp.producto_id = ? AND p.activo
                )
                """,
                Boolean.class,
                proveedorId,
                productoId);
        return Boolean.TRUE.equals(result);
    }

    public boolean productoProveedorRegistrado(UUID proveedorId, UUID productoId) {
        return existe(
                """
                SELECT EXISTS (SELECT 1 FROM public.producto_proveedor
                    WHERE proveedor_id = ? AND producto_id = ?)
                """,
                proveedorId,
                productoId);
    }

    public void vincularProveedorProducto(UUID proveedorId, UUID productoId) {
        jdbc.update(
                "INSERT INTO public.producto_proveedor (id, producto_id, proveedor_id)"
                        + " VALUES (?, ?, ?)",
                UUID.randomUUID(),
                productoId,
                proveedorId);
    }

    public UUID crearOrden(OrdenCompra orden) {
        jdbc.update(
                """
                INSERT INTO public.orden_compra
                    (id, numero, proveedor_id, pais_id, moneda_id, tipo_cambio, estado,
                     creada_por_id, creada_en_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                orden.id(),
                orden.numero(),
                orden.proveedorId(),
                orden.paisId(),
                orden.monedaId(),
                orden.tipoCambio(),
                orden.estado(),
                orden.creadaPorId(),
                orden.creadaEnUtc());
        return orden.id();
    }

    public void agregarLineaOrden(
            UUID id,
            UUID ordenId,
            UUID productoId,
            int cantidad,
            BigDecimal costoUnitario) {
        jdbc.update(
                """
                INSERT INTO public.orden_compra_linea
                    (id, orden_compra_id, producto_id, cantidad_pedida, costo_unitario)
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                ordenId,
                productoId,
                cantidad,
                costoUnitario);
    }

    public Page<OrdenCompra> listarOrdenes(
            UUID proveedorId,
            String estado,
            Set<String> paisesPermitidos,
            Pageable pageable) {
        StringBuilder from = new StringBuilder("""
                FROM public.orden_compra oc
                JOIN public.proveedor p ON p.id = oc.proveedor_id
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (proveedorId != null) {
            from.append(" AND oc.proveedor_id = ?");
            args.add(proveedorId);
        }
        if (estado != null) {
            from.append(" AND oc.estado = ?");
            args.add(estado);
        }
        agregarFiltroPaises(from, args, paisesPermitidos, "oc.pais_id");
        return pagina(
                "oc.id, oc.numero, oc.proveedor_id, p.razon_social AS proveedor_nombre,"
                        + " oc.pais_id, oc.moneda_id, oc.tipo_cambio, oc.estado,"
                        + " oc.creada_por_id, oc.creada_en_utc",
                from.toString(),
                "oc.creada_en_utc DESC",
                ORDEN_MAPPER,
                pageable,
                args);
    }

    public Optional<OrdenCompra> buscarOrden(UUID id) {
        return uno(
                """
                SELECT oc.id, oc.numero, oc.proveedor_id, p.razon_social AS proveedor_nombre,
                       oc.pais_id, oc.moneda_id, oc.tipo_cambio, oc.estado,
                       oc.creada_por_id, oc.creada_en_utc
                FROM public.orden_compra oc
                JOIN public.proveedor p ON p.id = oc.proveedor_id
                WHERE oc.id = ?
                """,
                ORDEN_MAPPER,
                id);
    }

    public void bloquearOrden(UUID id) {
        jdbc.query(
                "SELECT id FROM public.orden_compra WHERE id = ? FOR UPDATE",
                (rs, row) -> uuid(rs, "id"),
                id);
    }

    public List<OrdenCompraLinea> listarLineasOrden(UUID ordenId) {
        return jdbc.query(
                """
                SELECT ocl.id, ocl.producto_id, p.codigo_interno AS codigo_producto,
                       COALESCE(pt.nombre, p.codigo_interno) AS nombre_producto,
                       ocl.cantidad_pedida, ocl.costo_unitario,
                       COALESCE(recibido.cantidad, 0)::INTEGER AS cantidad_recibida
                FROM public.orden_compra_linea ocl
                JOIN public.producto p ON p.id = ocl.producto_id
                LEFT JOIN public.producto_traduccion pt ON pt.producto_id = p.id
                    AND pt.idioma_id = (SELECT id FROM public.idioma
                        WHERE lower(codigo_bcp47) = 'es' LIMIT 1)
                LEFT JOIN (
                    SELECT orden_compra_linea_id, SUM(cantidad) AS cantidad
                    FROM public.entrada_inventario_linea GROUP BY orden_compra_linea_id
                ) recibido ON recibido.orden_compra_linea_id = ocl.id
                WHERE ocl.orden_compra_id = ?
                ORDER BY p.codigo_interno
                """,
                ORDEN_LINEA_MAPPER,
                ordenId);
    }

    public Optional<LineaRecepcion> buscarLineaRecepcion(UUID ordenId, UUID lineaId) {
        return uno(
                """
                SELECT ocl.id, ocl.producto_id, ocl.cantidad_pedida, ocl.costo_unitario,
                       COALESCE(SUM(eil.cantidad), 0)::INTEGER AS cantidad_recibida
                FROM public.orden_compra_linea ocl
                LEFT JOIN public.entrada_inventario_linea eil
                    ON eil.orden_compra_linea_id = ocl.id
                WHERE ocl.orden_compra_id = ? AND ocl.id = ?
                GROUP BY ocl.id
                """,
                (rs, row) -> new LineaRecepcion(
                        uuid(rs, "id"),
                        uuid(rs, "producto_id"),
                        rs.getInt("cantidad_pedida"),
                        rs.getInt("cantidad_recibida"),
                        rs.getBigDecimal("costo_unitario")),
                ordenId,
                lineaId);
    }

    public boolean tieneLineasPendientes(UUID ordenId) {
        Boolean result = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM public.orden_compra_linea ocl
                    WHERE ocl.orden_compra_id = ?
                      AND ocl.cantidad_pedida > COALESCE((
                          SELECT SUM(eil.cantidad) FROM public.entrada_inventario_linea eil
                          WHERE eil.orden_compra_linea_id = ocl.id
                      ), 0)
                )
                """,
                Boolean.class,
                ordenId);
        return Boolean.TRUE.equals(result);
    }

    public boolean actualizarEstadoOrden(UUID id, String estado) {
        return jdbc.update(
                "UPDATE public.orden_compra SET estado = ? WHERE id = ?",
                estado,
                id) == 1;
    }

    public boolean ordenTieneEntradas(UUID ordenId) {
        return existe(
                "SELECT EXISTS (SELECT 1 FROM public.entrada_inventario"
                        + " WHERE orden_compra_id = ?)",
                ordenId);
    }

    public boolean documentoEntradaExiste(UUID ordenId, String documento) {
        Boolean result = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM public.entrada_inventario
                    WHERE orden_compra_id = ? AND lower(numero_documento) = lower(?)
                )
                """,
                Boolean.class,
                ordenId,
                documento);
        return Boolean.TRUE.equals(result);
    }

    public UUID crearEntrada(EntradaInventario entrada) {
        jdbc.update(
                """
                INSERT INTO public.entrada_inventario
                    (id, orden_compra_id, bodega_id, numero_documento,
                     tipo_cambio_aplicado, recibida_por_id, recibida_en_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                entrada.id(),
                entrada.ordenCompraId(),
                entrada.bodegaId(),
                entrada.numeroDocumento(),
                entrada.tipoCambioAplicado(),
                entrada.recibidaPorId(),
                entrada.recibidaEnUtc());
        return entrada.id();
    }

    public void agregarLineaEntrada(
            UUID id,
            UUID entradaId,
            UUID lineaOrdenId,
            UUID productoId,
            int cantidad,
            BigDecimal costoUnitario,
            BigDecimal costoBase) {
        jdbc.update(
                """
                INSERT INTO public.entrada_inventario_linea
                    (id, entrada_inventario_id, orden_compra_linea_id, producto_id,
                     cantidad, costo_unitario, costo_unitario_moneda_base)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                entradaId,
                lineaOrdenId,
                productoId,
                cantidad,
                costoUnitario,
                costoBase);
    }

    public Optional<EntradaInventario> buscarEntrada(UUID id) {
        return uno(
                """
                SELECT ei.id, ei.orden_compra_id, oc.numero AS numero_orden, ei.bodega_id,
                       ei.numero_documento, ei.tipo_cambio_aplicado, ei.recibida_por_id,
                       ei.recibida_en_utc
                FROM public.entrada_inventario ei
                JOIN public.orden_compra oc ON oc.id = ei.orden_compra_id
                WHERE ei.id = ?
                """,
                ENTRADA_MAPPER,
                id);
    }

    public List<EntradaInventarioLinea> listarLineasEntrada(UUID entradaId) {
        return jdbc.query(
                """
                SELECT eil.id, eil.orden_compra_linea_id, eil.producto_id,
                       p.codigo_interno AS codigo_producto, eil.cantidad,
                       eil.costo_unitario, eil.costo_unitario_moneda_base
                FROM public.entrada_inventario_linea eil
                JOIN public.producto p ON p.id = eil.producto_id
                WHERE eil.entrada_inventario_id = ?
                ORDER BY p.codigo_interno
                """,
                ENTRADA_LINEA_MAPPER,
                entradaId);
    }

    public boolean bodegaActiva(UUID id) {
        return existe(
                "SELECT EXISTS (SELECT 1 FROM public.bodega WHERE id = ? AND activo)",
                id);
    }

    public int cantidadDisponible(UUID productoId, UUID bodegaId) {
        return jdbc.queryForObject(
                """
                SELECT COALESCE(
                    (SELECT cantidad FROM public.saldo_inventario
                     WHERE producto_id = ? AND bodega_id = ?),
                    0)
                """,
                Integer.class,
                productoId,
                bodegaId);
    }

    public int bloquearSaldo(UUID productoId, UUID bodegaId, OffsetDateTime ahora) {
        jdbc.update(
                """
                INSERT INTO public.saldo_inventario
                    (id, producto_id, bodega_id, cantidad, actualizado_en_utc)
                VALUES (?, ?, ?, 0, ?)
                ON CONFLICT (producto_id, bodega_id) DO NOTHING
                """,
                UUID.randomUUID(),
                productoId,
                bodegaId,
                ahora);
        return jdbc.queryForObject(
                """
                SELECT cantidad FROM public.saldo_inventario
                WHERE producto_id = ? AND bodega_id = ? FOR UPDATE
                """,
                Integer.class,
                productoId,
                bodegaId);
    }

    public void actualizarSaldo(
            UUID productoId,
            UUID bodegaId,
            int cantidad,
            OffsetDateTime ahora) {
        jdbc.update(
                """
                UPDATE public.saldo_inventario
                SET cantidad = ?, actualizado_en_utc = ?
                WHERE producto_id = ? AND bodega_id = ?
                """,
                cantidad,
                ahora,
                productoId,
                bodegaId);
    }

    public Page<SaldoInventario> listarSaldos(
            UUID productoId,
            UUID bodegaId,
            boolean soloConExistencia,
            Pageable pageable) {
        return listarSaldos(
                productoId, bodegaId, null, Set.of(), soloConExistencia, pageable);
    }

    public Page<SaldoInventario> listarSaldos(
            UUID productoId,
            UUID bodegaId,
            UUID paisId,
            Set<String> paisesPermitidos,
            boolean soloConExistencia,
            Pageable pageable) {
        StringBuilder from = new StringBuilder("""
                FROM public.saldo_inventario si
                JOIN public.producto p ON p.id = si.producto_id
                JOIN public.bodega b ON b.id = si.bodega_id
                LEFT JOIN public.producto_traduccion pt ON pt.producto_id = p.id
                    AND pt.idioma_id = (SELECT id FROM public.idioma
                        WHERE lower(codigo_bcp47) LIKE 'es%' LIMIT 1)
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (productoId != null) {
            from.append(" AND si.producto_id = ?");
            args.add(productoId);
        }
        if (bodegaId != null) {
            from.append(" AND si.bodega_id = ?");
            args.add(bodegaId);
        }
        if (paisId != null) {
            from.append(" AND b.pais_id = ?");
            args.add(paisId);
        }
        agregarFiltroPaises(from, args, paisesPermitidos);
        if (soloConExistencia) {
            from.append(" AND si.cantidad > 0");
        }
        return pagina(
                """
                si.producto_id, p.codigo_interno AS codigo_producto,
                COALESCE(pt.nombre, p.codigo_interno) AS nombre_producto,
                si.bodega_id, b.codigo AS bodega_codigo, b.nombre AS bodega_nombre,
                si.cantidad, si.actualizado_en_utc
                """,
                from.toString(),
                "p.codigo_interno, b.codigo",
                SALDO_MAPPER,
                pageable,
                args);
    }

    public void crearMovimiento(MovimientoInventario movimiento) {
        jdbc.update(
                """
                INSERT INTO public.movimiento_inventario
                    (id, producto_id, bodega_id, tipo, cantidad, referencia_tipo,
                     referencia_id, usuario_id, fecha_utc, observacion)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                movimiento.id(),
                movimiento.productoId(),
                movimiento.bodegaId(),
                movimiento.tipo(),
                movimiento.cantidad(),
                movimiento.referenciaTipo(),
                movimiento.referenciaId(),
                movimiento.usuarioId(),
                movimiento.fechaUtc(),
                movimiento.observacion());
    }

    public Page<MovimientoInventario> listarMovimientos(
            UUID productoId,
            UUID bodegaId,
            UUID usuarioId,
            String tipo,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            UUID paisId,
            UUID monedaId,
            Pageable pageable) {
        return listarMovimientos(
                productoId,
                bodegaId,
                usuarioId,
                tipo,
                desde,
                hasta,
                paisId,
                monedaId,
                Set.of(),
                pageable);
    }

    public Page<MovimientoInventario> listarMovimientos(
            UUID productoId,
            UUID bodegaId,
            UUID usuarioId,
            String tipo,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            UUID paisId,
            UUID monedaId,
            Set<String> paisesPermitidos,
            Pageable pageable) {
        StringBuilder from = new StringBuilder("""
                FROM public.movimiento_inventario mi
                JOIN public.producto p ON p.id = mi.producto_id
                JOIN public.bodega b ON b.id = mi.bodega_id
                LEFT JOIN public.venta v
                    ON mi.referencia_tipo = 'VENTA' AND v.id = mi.referencia_id
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (productoId != null) {
            from.append(" AND mi.producto_id = ?");
            args.add(productoId);
        }
        if (bodegaId != null) {
            from.append(" AND mi.bodega_id = ?");
            args.add(bodegaId);
        }
        if (usuarioId != null) {
            from.append(" AND mi.usuario_id = ?");
            args.add(usuarioId);
        }
        if (tipo != null && !tipo.isBlank()) {
            from.append(" AND mi.tipo = ?");
            args.add(tipo.trim().toUpperCase());
        }
        if (desde != null) {
            from.append(" AND mi.fecha_utc >= ?");
            args.add(desde);
        }
        if (hasta != null) {
            from.append(" AND mi.fecha_utc <= ?");
            args.add(hasta);
        }
        if (paisId != null) {
            from.append(" AND COALESCE(v.pais_id, b.pais_id) = ?");
            args.add(paisId);
        }
        if (monedaId != null) {
            from.append(" AND v.moneda_id = ?");
            args.add(monedaId);
        }
        agregarFiltroPaises(from, args, paisesPermitidos);
        return pagina(
                "mi.id, mi.producto_id, p.codigo_interno AS codigo_producto,"
                        + " mi.bodega_id, b.nombre AS bodega_nombre, mi.tipo, mi.cantidad,"
                        + " mi.referencia_tipo, mi.referencia_id, mi.usuario_id, mi.fecha_utc,"
                        + " mi.observacion",
                from.toString(),
                "mi.fecha_utc DESC",
                MOVIMIENTO_MAPPER,
                pageable,
                args);
    }

    public Optional<MovimientoInventario> buscarMovimiento(UUID id) {
        return uno(
                """
                SELECT mi.id, mi.producto_id, p.codigo_interno AS codigo_producto,
                       mi.bodega_id, b.nombre AS bodega_nombre, mi.tipo, mi.cantidad,
                       mi.referencia_tipo, mi.referencia_id, mi.usuario_id, mi.fecha_utc,
                       mi.observacion
                FROM public.movimiento_inventario mi
                JOIN public.producto p ON p.id = mi.producto_id
                JOIN public.bodega b ON b.id = mi.bodega_id
                WHERE mi.id = ?
                """,
                MOVIMIENTO_MAPPER,
                id);
    }

    public boolean actualizarAjusteRevalidado(
            UUID id,
            int cantidadSistema,
            int diferencia) {
        return jdbc.update(
                """
                UPDATE public.ajuste_inventario
                SET estado = 'PENDIENTE_DE_APROBACION',
                    cantidad_sistema = ?, diferencia = ?,
                    aprobado_por_id = NULL, aprobado_en_utc = NULL
                WHERE id = ? AND estado = 'APROBADO'
                """,
                cantidadSistema,
                diferencia,
                id) == 1;
    }

    public UUID crearAjuste(AjusteInventario ajuste) {
        jdbc.update(
                """
                INSERT INTO public.ajuste_inventario
                    (id, producto_id, bodega_id, tipo, cantidad_sistema, cantidad_contada,
                     diferencia, motivo, estado, solicitado_por_id, solicitado_en_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ajuste.id(),
                ajuste.productoId(),
                ajuste.bodegaId(),
                ajuste.tipo(),
                ajuste.cantidadSistema(),
                ajuste.cantidadContada(),
                ajuste.diferencia(),
                ajuste.motivo(),
                ajuste.estado(),
                ajuste.solicitadoPorId(),
                ajuste.solicitadoEnUtc());
        return ajuste.id();
    }

    public Optional<AjusteInventario> buscarAjuste(UUID id) {
        return uno(
                """
                SELECT ai.id, ai.producto_id, p.codigo_interno AS codigo_producto,
                       ai.bodega_id, b.nombre AS bodega_nombre, ai.tipo,
                       ai.cantidad_sistema, ai.cantidad_contada, ai.diferencia, ai.motivo,
                       ai.estado, ai.solicitado_por_id, ai.aprobado_por_id,
                       ai.solicitado_en_utc, ai.aprobado_en_utc, ai.aplicado_en_utc
                FROM public.ajuste_inventario ai
                JOIN public.producto p ON p.id = ai.producto_id
                JOIN public.bodega b ON b.id = ai.bodega_id
                WHERE ai.id = ?
                """,
                AJUSTE_MAPPER,
                id);
    }

    public void bloquearAjuste(UUID id) {
        jdbc.query(
                "SELECT id FROM public.ajuste_inventario WHERE id = ? FOR UPDATE",
                (rs, row) -> uuid(rs, "id"),
                id);
    }

    public boolean actualizarAjusteAprobado(
            UUID id,
            UUID aprobadorId,
            OffsetDateTime ahora) {
        return jdbc.update(
                """
                UPDATE public.ajuste_inventario
                SET estado = 'APROBADO', aprobado_por_id = ?, aprobado_en_utc = ?
                WHERE id = ? AND estado = 'PENDIENTE_DE_APROBACION'
                """,
                aprobadorId,
                ahora,
                id) == 1;
    }

    public boolean actualizarAjusteAplicado(UUID id, OffsetDateTime ahora) {
        return jdbc.update(
                """
                UPDATE public.ajuste_inventario
                SET estado = 'APLICADO', aplicado_en_utc = ?
                WHERE id = ? AND estado = 'APROBADO'
                """,
                ahora,
                id) == 1;
    }

    public Page<AjusteInventario> listarAjustes(
            String estado,
            String tipo,
            UUID productoId,
            UUID bodegaId,
            Set<String> paisesPermitidos,
            Pageable pageable) {
        StringBuilder from = new StringBuilder("""
                FROM public.ajuste_inventario ai
                JOIN public.producto p ON p.id = ai.producto_id
                JOIN public.bodega b ON b.id = ai.bodega_id
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (estado != null) {
            from.append(" AND ai.estado = ?");
            args.add(estado);
        }
        if (tipo != null) {
            from.append(" AND ai.tipo = ?");
            args.add(tipo);
        }
        if (productoId != null) {
            from.append(" AND ai.producto_id = ?");
            args.add(productoId);
        }
        if (bodegaId != null) {
            from.append(" AND ai.bodega_id = ?");
            args.add(bodegaId);
        }
        agregarFiltroPaises(from, args, paisesPermitidos, "b.pais_id");
        return pagina(
                "ai.id, ai.producto_id, p.codigo_interno AS codigo_producto,"
                        + " ai.bodega_id, b.nombre AS bodega_nombre, ai.tipo,"
                        + " ai.cantidad_sistema, ai.cantidad_contada, ai.diferencia, ai.motivo,"
                        + " ai.estado, ai.solicitado_por_id, ai.aprobado_por_id,"
                        + " ai.solicitado_en_utc, ai.aprobado_en_utc, ai.aplicado_en_utc",
                from.toString(),
                "ai.solicitado_en_utc DESC",
                AJUSTE_MAPPER,
                pageable,
                args);
    }

    private void agregarFiltroPaises(
            StringBuilder from,
            List<Object> args,
            Set<String> paisesPermitidos) {
        agregarFiltroPaises(from, args, paisesPermitidos, "b.pais_id");
    }

    private void agregarFiltroPaises(
            StringBuilder from,
            List<Object> args,
            Set<String> paisesPermitidos,
            String columnaPais) {
        if (paisesPermitidos == null || paisesPermitidos.isEmpty()) {
            return;
        }
        List<String> codigos = paisesPermitidos.stream().sorted().toList();
        from.append(" AND ")
                .append(columnaPais)
                .append(" IN (SELECT pais.id FROM public.pais pais")
                .append(" WHERE pais.codigo_iso IN (")
                .append(String.join(",", codigos.stream().map(codigo -> "?").toList()))
                .append("))");
        args.addAll(codigos);
    }

    private <T> Page<T> pagina(
            String columnas,
            String from,
            String orderBy,
            RowMapper<T> mapper,
            Pageable pageable,
            List<Object> argumentos) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) " + from,
                Long.class,
                argumentos.toArray());
        List<Object> dataArgs = new ArrayList<>(argumentos);
        dataArgs.add(pageable.getPageSize());
        dataArgs.add(pageable.getOffset());
        List<T> content = jdbc.query(
                "SELECT " + columnas + " " + from + " ORDER BY " + orderBy
                        + " LIMIT ? OFFSET ?",
                mapper,
                dataArgs.toArray());
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private <T> Optional<T> uno(String sql, RowMapper<T> mapper, Object... args) {
        return jdbc.query(sql, mapper, args).stream().findFirst();
    }

    private boolean existe(String sql, Object... args) {
        Boolean result = jdbc.queryForObject(sql, Boolean.class, args);
        return Boolean.TRUE.equals(result);
    }

    private static UUID uuid(ResultSet rs, String field) throws SQLException {
        return rs.getObject(field, UUID.class);
    }
}
