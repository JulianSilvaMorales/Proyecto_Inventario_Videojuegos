package com.inventario.service;

import com.inventario.domain.Bodega;
import com.inventario.domain.MovimientoInventario;
import com.inventario.domain.Pais;
import com.inventario.domain.Proveedor;
import com.inventario.domain.SaldoInventario;
import com.inventario.dto.ReferenciaOpcion;
import com.inventario.dto.SolicitudBodega;
import com.inventario.dto.SolicitudProveedor;
import com.inventario.dto.SolicitudSalidaInventario;
import com.inventario.repository.ConfiguracionRepository;
import com.inventario.repository.InventarioRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private final ConfiguracionRepository configuracion;
    private final AuditoriaService auditoria;
    private final AccesoPaisService accesoPais;
    private final Clock clock;

    public InventarioService(
            InventarioRepository repository,
            ConfiguracionRepository configuracion,
            AuditoriaService auditoria,
            AccesoPaisService accesoPais,
            Clock clock) {
        this.repository = repository;
        this.configuracion = configuracion;
        this.auditoria = auditoria;
        this.accesoPais = accesoPais;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Bodega> listarBodegas(String buscar, Pageable pageable) {
        return repository.listarBodegas(buscar, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Bodega> listarBodegas(
            String buscar,
            Set<String> paisesPermitidos,
            Pageable pageable) {
        return repository.listarBodegas(buscar, paisesPermitidos, pageable);
    }

    @Transactional(readOnly = true)
    public Bodega obtenerBodega(UUID id) {
        return requerir(
                repository.buscarBodega(id),
                HttpStatus.NOT_FOUND,
                "error.inventory.warehouse.not-found");
    }

    @Transactional(readOnly = true)
    public Bodega obtenerBodega(UUID id, Set<String> paisesPermitidos) {
        Bodega bodega = obtenerBodega(id);
        validarAccesoPais(bodega, paisesPermitidos);
        return bodega;
    }

    @Transactional
    public Bodega crearBodega(
            SolicitudBodega solicitud,
            UUID actorId,
            Set<String> paisesPermitidos) {
        validarCategoria(solicitud.categoriaId());
        Pais pais = validarPaisBodega(solicitud.paisId(), paisesPermitidos);
        Bodega nueva = new Bodega(
                null,
                solicitud.codigo().trim().toUpperCase(Locale.ROOT),
                solicitud.nombre().trim(),
                solicitud.categoriaId(),
                pais.id(),
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
            UUID actorId,
            Set<String> paisesPermitidos) {
        repository.bloquearBodega(id);
        Bodega anterior = obtenerBodega(id);
        validarAccesoPais(anterior, paisesPermitidos);
        validarCategoria(solicitud.categoriaId());
        Pais pais = validarPaisBodega(solicitud.paisId(), paisesPermitidos);
        if (anterior.paisId() != null && !anterior.paisId().equals(pais.id())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.warehouse.country.immutable");
        }
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
                pais.id(),
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
    public Page<Proveedor> listarProveedores(
            String buscar,
            Set<String> paisesPermitidos,
            Pageable pageable) {
        return repository.listarProveedores(buscar, paisesPermitidos, pageable);
    }

    @Transactional(readOnly = true)
    public Proveedor obtenerProveedor(UUID id, Set<String> paisesPermitidos) {
        Proveedor proveedor = requerir(
                repository.buscarProveedor(id),
                HttpStatus.NOT_FOUND,
                "error.inventory.supplier.not-found");
        validarAccesoPais(proveedor.paisId(), paisesPermitidos);
        return proveedor;
    }

    @Transactional
    public Proveedor crearProveedor(
            SolicitudProveedor solicitud,
            UUID actorId,
            Set<String> paisesPermitidos) {
        validarReferenciasProveedor(solicitud);
        validarAccesoPais(solicitud.paisId(), paisesPermitidos);
        Proveedor nuevo = proveedor(null, solicitud, solicitud.activo());
        UUID id = repository.crearProveedor(nuevo);
        Proveedor creado = obtenerProveedor(id, paisesPermitidos);
        auditoria.registrar(actorId, null, "CREAR", "proveedor", id, null, creado);
        return creado;
    }

    @Transactional
    public Proveedor actualizarProveedor(
            UUID id,
            SolicitudProveedor solicitud,
            UUID actorId,
            Set<String> paisesPermitidos) {
        repository.bloquearProveedor(id);
        Proveedor anterior = obtenerProveedor(id, paisesPermitidos);
        validarReferenciasProveedor(solicitud);
        validarAccesoPais(solicitud.paisId(), paisesPermitidos);
        if (!Objects.equals(anterior.paisId(), solicitud.paisId())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.supplier.country.immutable");
        }
        if (!repository.actualizarProveedor(proveedor(id, solicitud, solicitud.activo()))) {
            throw error(HttpStatus.NOT_FOUND, "error.inventory.supplier.not-found");
        }
        Proveedor actualizado = obtenerProveedor(id, paisesPermitidos);
        auditoria.registrar(
                actorId, null, "ACTUALIZAR", "proveedor", id, anterior, actualizado);
        return actualizado;
    }

    @Transactional
    public void vincularProductoProveedor(
            UUID proveedorId,
            UUID productoId,
            UUID actorId,
            Set<String> paisesPermitidos) {
        repository.bloquearProveedor(proveedorId);
        Proveedor proveedor = obtenerProveedor(proveedorId, paisesPermitidos);
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
    public List<ReferenciaOpcion> listarProductosProveedor(
            UUID proveedorId,
            String idioma,
            Set<String> paisesPermitidos) {
        obtenerProveedor(proveedorId, paisesPermitidos);
        return repository.listarProductosProveedor(proveedorId, idioma);
    }

    @Transactional(readOnly = true)
    public Page<SaldoInventario> listarSaldos(
            UUID productoId,
            UUID bodegaId,
            boolean soloConExistencia,
            Pageable pageable) {
        return repository.listarSaldos(productoId, bodegaId, soloConExistencia, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SaldoInventario> listarSaldos(
            UUID productoId,
            UUID bodegaId,
            UUID paisId,
            Set<String> paisesPermitidos,
            boolean soloConExistencia,
            Pageable pageable) {
        validarPaisFiltro(paisId, paisesPermitidos);
        return repository.listarSaldos(
                productoId,
                bodegaId,
                paisId,
                paisesPermitidos,
                soloConExistencia,
                pageable);
    }

    @Transactional
    public MovimientoInventario registrarSalida(
            SolicitudSalidaInventario solicitud,
            UUID actorId,
            Set<String> paisesPermitidos) {
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
        Bodega bodega = obtenerBodega(solicitud.bodegaId());
        validarAccesoPais(bodega, paisesPermitidos);
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
        return listarMovimientos(
                productoId, bodegaId, usuarioId, tipo, desde, hasta, null, null, pageable);
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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
        validarPaisFiltro(paisId, paisesPermitidos);
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
                productoId,
                bodegaId,
                usuarioId,
                tipoNormalizado,
                desde,
                hasta,
                paisId,
                monedaId,
                paisesPermitidos,
                pageable);
    }

    @Transactional(readOnly = true)
    public MovimientoInventario obtenerMovimiento(UUID id) {
        return requerir(
                repository.buscarMovimiento(id),
                HttpStatus.NOT_FOUND,
                "error.inventory.history.not-found");
    }

    @Transactional(readOnly = true)
    public MovimientoInventario obtenerMovimiento(UUID id, Set<String> paisesPermitidos) {
        MovimientoInventario movimiento = obtenerMovimiento(id);
        Bodega bodega = obtenerBodega(movimiento.bodegaId());
        validarAccesoPais(bodega, paisesPermitidos);
        return movimiento;
    }

    private Pais validarPaisBodega(UUID paisId, Set<String> paisesPermitidos) {
        Pais pais = configuracion.buscarPais(paisId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "error.sales.country.not-found"));
        if (!pais.habilitado()) {
            throw error(HttpStatus.CONFLICT, "error.sales.country.disabled");
        }
        accesoPais.validar(paisesPermitidos, pais.codigoIso());
        return pais;
    }

    private void validarAccesoPais(Bodega bodega, Set<String> paisesPermitidos) {
        if (bodega.paisId() == null) {
            if (paisesPermitidos != null && !paisesPermitidos.isEmpty()) {
                throw error(HttpStatus.FORBIDDEN, "error.country.access.denied");
            }
            return;
        }
        Pais pais = configuracion.buscarPais(bodega.paisId())
                .orElseThrow(() -> new IllegalStateException(
                        "La bodega referencia un país que no existe."));
        accesoPais.validar(paisesPermitidos, pais);
    }

    private void validarAccesoPais(UUID paisId, Set<String> paisesPermitidos) {
        Pais pais = paisId == null
                ? null
                : configuracion.buscarPais(paisId)
                        .orElseThrow(() -> new IllegalStateException(
                                "El proveedor referencia un país que no existe."));
        accesoPais.validar(paisesPermitidos, pais);
    }

    private void validarPaisFiltro(UUID paisId, Set<String> paisesPermitidos) {
        if (paisId == null || paisesPermitidos == null || paisesPermitidos.isEmpty()) {
            return;
        }
        Pais pais = configuracion.buscarPais(paisId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "error.sales.country.not-found"));
        accesoPais.validar(paisesPermitidos, pais.codigoIso());
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
