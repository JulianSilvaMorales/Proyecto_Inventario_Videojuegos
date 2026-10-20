package com.inventario.service;

import com.inventario.domain.Bodega;
import com.inventario.domain.MovimientoInventario;
import com.inventario.domain.Proveedor;
import com.inventario.domain.SaldoInventario;
import com.inventario.dto.SolicitudBodega;
import com.inventario.dto.SolicitudProveedor;
import com.inventario.dto.SolicitudSalidaInventario;
import com.inventario.repository.InventarioRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventarioService {

    private static final Map<String, String> TIPOS_SALIDA = Map.of(
            "PERDIDA", "SALIDA_PERDIDA",
            "DANO", "SALIDA_DANO",
            "ROBO", "SALIDA_ROBO",
            "DEFECTO", "SALIDA_DEFECTO");
    private static final Set<String> TIPOS_MOVIMIENTO = Set.of(
            "ENTRADA",
            "SALIDA_VENTA",
            "SALIDA_PERDIDA",
            "SALIDA_DANO",
            "SALIDA_ROBO",
            "SALIDA_DEFECTO",
            "AJUSTE");

    private final InventarioRepository repository;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public InventarioService(
            InventarioRepository repository,
            AuditoriaService auditoria,
            Clock clock) {
        this.repository = repository;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Bodega> listarBodegas(String buscar, Pageable pageable) {
        return repository.listarBodegas(buscar, pageable);
    }

    @Transactional(readOnly = true)
    public Bodega obtenerBodega(UUID id) {
        return requerir(
                repository.buscarBodega(id),
                HttpStatus.NOT_FOUND,
                "error.inventory.warehouse.not-found");
    }

    @Transactional
    public Bodega crearBodega(SolicitudBodega solicitud, UUID actorId) {
        validarCategoria(solicitud.categoriaId());
        Bodega nueva = new Bodega(
                null,
                solicitud.codigo().trim().toUpperCase(Locale.ROOT),
                solicitud.nombre().trim(),
                solicitud.categoriaId(),
                solicitud.activo());
        UUID id = repository.crearBodega(nueva);
        Bodega creada = obtenerBodega(id);
        auditoria.registrar(actorId, null, "CREAR", "bodega", id, null, creada);
        return creada;
    }

    @Transactional
    public Bodega actualizarBodega(
            UUID id,
            SolicitudBodega solicitud,
            UUID actorId) {
        repository.bloquearBodega(id);
        Bodega anterior = obtenerBodega(id);
        validarCategoria(solicitud.categoriaId());
        if (anterior.activo() && !solicitud.activo()
                && (repository.bodegaConSaldoPositivo(id)
                        || repository.bodegaConAjustesAbiertos(id))) {
            throw error(HttpStatus.CONFLICT, "error.inventory.warehouse.has-stock");
        }
        Bodega actualizada = new Bodega(
                id,
                solicitud.codigo().trim().toUpperCase(Locale.ROOT),
                solicitud.nombre().trim(),
                solicitud.categoriaId(),
                solicitud.activo());
        if (!repository.actualizarBodega(actualizada)) {
            throw error(HttpStatus.NOT_FOUND, "error.inventory.warehouse.not-found");
        }
        Bodega resultado = obtenerBodega(id);
        auditoria.registrar(
                actorId, null, "ACTUALIZAR", "bodega", id, anterior, resultado);
        return resultado;
    }

    @Transactional(readOnly = true)
    public Page<Proveedor> listarProveedores(String buscar, Pageable pageable) {
        return repository.listarProveedores(buscar, pageable);
    }

    @Transactional(readOnly = true)
    public Proveedor obtenerProveedor(UUID id) {
        return requerir(
                repository.buscarProveedor(id),
                HttpStatus.NOT_FOUND,
                "error.inventory.supplier.not-found");
    }

    @Transactional
    public Proveedor crearProveedor(SolicitudProveedor solicitud, UUID actorId) {
        validarReferenciasProveedor(solicitud);
        Proveedor nuevo = proveedor(null, solicitud, solicitud.activo());
        UUID id = repository.crearProveedor(nuevo);
        Proveedor creado = obtenerProveedor(id);
        auditoria.registrar(actorId, null, "CREAR", "proveedor", id, null, creado);
        return creado;
    }

    @Transactional
    public Proveedor actualizarProveedor(
            UUID id,
            SolicitudProveedor solicitud,
            UUID actorId) {
        repository.bloquearProveedor(id);
        Proveedor anterior = obtenerProveedor(id);
        validarReferenciasProveedor(solicitud);
        if (!repository.actualizarProveedor(proveedor(id, solicitud, solicitud.activo()))) {
            throw error(HttpStatus.NOT_FOUND, "error.inventory.supplier.not-found");
        }
        Proveedor actualizado = obtenerProveedor(id);
        auditoria.registrar(
                actorId, null, "ACTUALIZAR", "proveedor", id, anterior, actualizado);
        return actualizado;
    }

    @Transactional
    public void vincularProductoProveedor(
            UUID proveedorId,
            UUID productoId,
            UUID actorId) {
        repository.bloquearProveedor(proveedorId);
        Proveedor proveedor = obtenerProveedor(proveedorId);
        if (!proveedor.activo()) {
            throw error(HttpStatus.CONFLICT, "error.inventory.supplier.inactive");
        }
        if (!repository.productoActivo(productoId)) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.product.not-found");
        }
        if (repository.productoProveedorRegistrado(proveedorId, productoId)) {
            throw error(HttpStatus.CONFLICT, "error.inventory.supplier-product.exists");
        }
        repository.vincularProveedorProducto(proveedorId, productoId);
        auditoria.registrar(
                actorId,
                null,
                "VINCULAR_PRODUCTO",
                "producto_proveedor",
                productoId,
                null,
                Map.of("proveedorId", proveedorId, "productoId", productoId));
    }

    @Transactional(readOnly = true)
    public Page<SaldoInventario> listarSaldos(
            UUID productoId,
            UUID bodegaId,
            boolean soloConExistencia,
            Pageable pageable) {
        return repository.listarSaldos(productoId, bodegaId, soloConExistencia, pageable);
    }

    @Transactional
    public MovimientoInventario registrarSalida(
            SolicitudSalidaInventario solicitud,
            UUID actorId) {
        String tipo = TIPOS_SALIDA.get(solicitud.tipo().trim().toUpperCase(Locale.ROOT));
        if (tipo == null && solicitud.tipo().trim().equalsIgnoreCase("DAÑO")) {
            tipo = TIPOS_SALIDA.get("DANO");
        }
        if (tipo == null) {
            throw error(HttpStatus.BAD_REQUEST, "error.inventory.exit.type");
        }
        if (!repository.productoExiste(solicitud.productoId())) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.product.not-found");
        }
        obtenerBodega(solicitud.bodegaId());
        repository.bloquearProveedor(solicitud.proveedorId());
        if (!repository.proveedorProducto(solicitud.proveedorId(), solicitud.productoId())) {
            throw error(HttpStatus.BAD_REQUEST, "error.inventory.exit.supplier");
        }
        OffsetDateTime ahora = ahora();
        int actual = repository.bloquearSaldo(
                solicitud.productoId(), solicitud.bodegaId(), ahora);
        if (actual < solicitud.cantidad()) {
            throw error(HttpStatus.CONFLICT, "error.inventory.stock.insufficient");
        }
        int nuevoSaldo = actual - solicitud.cantidad();
        repository.actualizarSaldo(
                solicitud.productoId(), solicitud.bodegaId(), nuevoSaldo, ahora);
        String observacion = "Reporte proveedor: "
                + solicitud.numeroReporteProveedor().trim();
        if (solicitud.observacion() != null && !solicitud.observacion().isBlank()) {
            observacion += " | " + solicitud.observacion().trim();
        }
        MovimientoInventario movimiento = new MovimientoInventario(
                UUID.randomUUID(),
                solicitud.productoId(),
                null,
                solicitud.bodegaId(),
                null,
                tipo,
                solicitud.cantidad(),
                "PROVEEDOR",
                solicitud.proveedorId(),
                actorId,
                ahora,
                observacion);
        repository.crearMovimiento(movimiento);
        movimiento = repository.buscarMovimiento(movimiento.id())
                .orElseThrow(() -> new IllegalStateException(
                        "No se pudo recuperar el movimiento de inventario creado."));
        auditoria.registrar(
                actorId, null, "SALIDA", "movimiento_inventario",
                movimiento.id(), null, movimiento);
        return movimiento;
    }

    @Transactional(readOnly = true)
    public Page<MovimientoInventario> listarMovimientos(
            UUID productoId,
            UUID bodegaId,
            UUID usuarioId,
            String tipo,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            Pageable pageable) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw error(HttpStatus.BAD_REQUEST, "error.inventory.history.dates");
        }
        String tipoNormalizado = tipo == null
                ? null
                : tipo.trim().toUpperCase(Locale.ROOT);
        if (tipoNormalizado != null && !TIPOS_MOVIMIENTO.contains(tipoNormalizado)) {
            throw error(HttpStatus.BAD_REQUEST, "error.inventory.history.type");
        }
        return repository.listarMovimientos(
                productoId, bodegaId, usuarioId, tipoNormalizado, desde, hasta, pageable);
    }

    @Transactional(readOnly = true)
    public MovimientoInventario obtenerMovimiento(UUID id) {
        return requerir(
                repository.buscarMovimiento(id),
                HttpStatus.NOT_FOUND,
                "error.inventory.history.not-found");
    }

    private void validarCategoria(UUID categoriaId) {
        if (categoriaId != null && !repository.categoriaActiva(categoriaId)) {
            throw error(HttpStatus.BAD_REQUEST, "error.inventory.warehouse.category");
        }
    }

    private void validarReferenciasProveedor(SolicitudProveedor solicitud) {
        if (!repository.paisExiste(solicitud.paisId())) {
            throw error(HttpStatus.NOT_FOUND, "error.pais.not-found");
        }
        if (!repository.monedaExiste(solicitud.monedaCompraId())) {
            throw error(HttpStatus.NOT_FOUND, "error.moneda.not-found");
        }
    }

    private Proveedor proveedor(
            UUID id,
            SolicitudProveedor solicitud,
            boolean activo) {
        return new Proveedor(
                id,
                solicitud.razonSocial().trim(),
                solicitud.paisId(),
                normalizar(solicitud.identificacionTributaria()),
                normalizar(solicitud.personaContacto()),
                normalizar(solicitud.correo()),
                normalizar(solicitud.telefono()),
                solicitud.monedaCompraId(),
                normalizar(solicitud.condicionesPago()),
                activo);
    }

    private static String normalizar(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private <T> T requerir(
            java.util.Optional<T> value,
            HttpStatus status,
            String messageKey) {
        return value.orElseThrow(() -> error(status, messageKey));
    }

    private ErrorAplicacionException error(HttpStatus status, String messageKey) {
        return new ErrorAplicacionException(status, messageKey);
    }
}
