package com.inventario.service;

import com.inventario.domain.Bodega;
import com.inventario.domain.DetalleEntradaInventario;
import com.inventario.domain.DetalleOrdenCompra;
import com.inventario.domain.EntradaInventario;
import com.inventario.domain.EntradaInventarioLinea;
import com.inventario.domain.LineaRecepcion;
import com.inventario.domain.MovimientoInventario;
import com.inventario.domain.OrdenCompra;
import com.inventario.domain.Pais;
import com.inventario.domain.Proveedor;
import com.inventario.dto.SolicitudEntradaInventario;
import com.inventario.dto.SolicitudLineaEntradaInventario;
import com.inventario.dto.SolicitudLineaOrdenCompra;
import com.inventario.dto.SolicitudOrdenCompra;
import com.inventario.repository.ConfiguracionRepository;
import com.inventario.repository.InventarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComprasService {

    private static final Set<String> ESTADOS_ORDEN = Set.of(
            "BORRADOR",
            "ENVIADA_AL_PROVEEDOR",
            "RECIBIDA_PARCIAL",
            "RECIBIDA_COMPLETA",
            "CANCELADA");

    private final InventarioRepository repository;
    private final ConfiguracionRepository configuracion;
    private final AuditoriaService auditoria;
    private final AccesoPaisService accesoPais;
    private final ParametroService parametros;
    private final Clock clock;

    public ComprasService(
            InventarioRepository repository,
            ConfiguracionRepository configuracion,
            AuditoriaService auditoria,
            AccesoPaisService accesoPais,
            ParametroService parametros,
            Clock clock) {
        this.repository = repository;
        this.configuracion = configuracion;
        this.auditoria = auditoria;
        this.accesoPais = accesoPais;
        this.parametros = parametros;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<OrdenCompra> listarOrdenes(
            UUID proveedorId,
            String estado,
            Set<String> paisesPermitidos,
            Pageable pageable) {
        if (estado != null && !ESTADOS_ORDEN.contains(estado.trim().toUpperCase(Locale.ROOT))) {
            throw error(HttpStatus.BAD_REQUEST, "error.inventory.order.state");
        }
        return repository.listarOrdenes(
                proveedorId,
                estado == null ? null : estado.trim().toUpperCase(Locale.ROOT),
                paisesPermitidos,
                pageable);
    }

    @Transactional(readOnly = true)
    public DetalleOrdenCompra obtenerOrden(UUID id, Set<String> paisesPermitidos) {
        OrdenCompra orden = ordenExistente(id);
        validarAccesoPais(orden.paisId(), paisesPermitidos);
        return new DetalleOrdenCompra(orden, repository.listarLineasOrden(id));
    }

    @Transactional
    public DetalleOrdenCompra crearOrden(
            SolicitudOrdenCompra solicitud,
            UUID actorId,
            Set<String> paisesPermitidos) {
        repository.bloquearProveedor(solicitud.proveedorId());
        Proveedor proveedor = repository.buscarProveedor(solicitud.proveedorId())
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "error.inventory.supplier.not-found"));
        validarAccesoPais(proveedor.paisId(), paisesPermitidos);
        if (!proveedor.activo()) {
            throw error(HttpStatus.CONFLICT, "error.inventory.supplier.inactive");
        }
        validarTipoCambio(proveedor.monedaCompraId(), solicitud.tipoCambio());
        Set<UUID> productos = new HashSet<>();
        for (SolicitudLineaOrdenCompra linea : solicitud.lineas()) {
            if (!productos.add(linea.productoId())) {
                throw error(HttpStatus.BAD_REQUEST, "error.inventory.order.duplicate-product");
            }
            if (!repository.productoActivo(linea.productoId())) {
                throw error(HttpStatus.NOT_FOUND, "error.catalog.product.not-found");
            }
            if (!repository.proveedorProducto(proveedor.id(), linea.productoId())) {
                throw error(HttpStatus.BAD_REQUEST, "error.inventory.order.supplier-product");
            }
        }

        UUID id = UUID.randomUUID();
        OrdenCompra orden = new OrdenCompra(
                id,
                "OC-" + id.toString().toUpperCase(Locale.ROOT),
                proveedor.id(),
                proveedor.razonSocial(),
                proveedor.paisId(),
                proveedor.monedaCompraId(),
                solicitud.tipoCambio(),
                "BORRADOR",
                actorId,
                ahora());
        repository.crearOrden(orden);
        for (SolicitudLineaOrdenCompra linea : solicitud.lineas()) {
            repository.agregarLineaOrden(
                    UUID.randomUUID(),
                    id,
                    linea.productoId(),
                    linea.cantidadPedida(),
                    linea.costoUnitario());
        }
        DetalleOrdenCompra creada = obtenerOrden(id, paisesPermitidos);
        auditoria.registrar(actorId, null, "CREAR", "orden_compra", id, null, creada);
        return creada;
    }

    @Transactional
    public DetalleOrdenCompra enviarOrden(
            UUID id,
            UUID actorId,
            Set<String> paisesPermitidos) {
        repository.bloquearOrden(id);
        OrdenCompra anterior = ordenExistente(id);
        validarAccesoPais(anterior.paisId(), paisesPermitidos);
        if (!"BORRADOR".equals(anterior.estado())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.order.transition");
        }
        Proveedor proveedor = repository.buscarProveedor(anterior.proveedorId())
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "error.inventory.supplier.not-found"));
        if (!proveedor.activo()) {
            throw error(HttpStatus.CONFLICT, "error.inventory.supplier.inactive");
        }
        repository.actualizarEstadoOrden(id, "ENVIADA_AL_PROVEEDOR");
        DetalleOrdenCompra actualizada = obtenerOrden(id, paisesPermitidos);
        auditoria.registrar(
                actorId, null, "ENVIAR", "orden_compra", id, anterior, actualizada);
        return actualizada;
    }

    @Transactional
    public DetalleOrdenCompra cancelarOrden(
            UUID id,
            UUID actorId,
            Set<String> paisesPermitidos) {
        repository.bloquearOrden(id);
        OrdenCompra anterior = ordenExistente(id);
        validarAccesoPais(anterior.paisId(), paisesPermitidos);
        if (!Set.of("BORRADOR", "ENVIADA_AL_PROVEEDOR").contains(anterior.estado())
                || repository.ordenTieneEntradas(id)) {
            throw error(HttpStatus.CONFLICT, "error.inventory.order.cancel");
        }
        repository.actualizarEstadoOrden(id, "CANCELADA");
        DetalleOrdenCompra actualizada = obtenerOrden(id, paisesPermitidos);
        auditoria.registrar(
                actorId, null, "CANCELAR", "orden_compra", id, anterior, actualizada);
        return actualizada;
    }

    @Transactional
    public DetalleEntradaInventario recibir(
            SolicitudEntradaInventario solicitud,
            UUID actorId,
            Set<String> paisesPermitidos) {
        repository.bloquearOrden(solicitud.ordenCompraId());
        OrdenCompra orden = ordenExistente(solicitud.ordenCompraId());
        validarAccesoPais(orden.paisId(), paisesPermitidos);
        if (!Set.of("ENVIADA_AL_PROVEEDOR", "RECIBIDA_PARCIAL").contains(orden.estado())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.order.receive-state");
        }
        repository.bloquearBodega(solicitud.bodegaId());
        Bodega bodega = repository.buscarBodega(solicitud.bodegaId())
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "error.inventory.warehouse.not-found"));
        validarAccesoPais(bodega.paisId(), paisesPermitidos);
        if (!Objects.equals(orden.paisId(), bodega.paisId())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.receipt.warehouse.country");
        }
        if (!repository.bodegaActiva(solicitud.bodegaId())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.warehouse.inactive");
        }
        validarTipoCambio(orden.monedaId(), solicitud.tipoCambioAplicado());
        String monedaBase = monedaBaseCodigo();
        Integer decimalesBase = repository.decimalesMoneda(monedaBase);
        if (decimalesBase == null) {
            throw new IllegalStateException(
                    "La moneda base configurada no existe en el catálogo de monedas.");
        }
        if (repository.documentoEntradaExiste(
                orden.id(), solicitud.numeroDocumento().trim())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.receipt.document-duplicate");
        }

        List<LineaRecibida> lineas = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (SolicitudLineaEntradaInventario solicitada : solicitud.lineas()) {
            if (!ids.add(solicitada.ordenCompraLineaId())) {
                throw error(HttpStatus.BAD_REQUEST, "error.inventory.receipt.duplicate-line");
            }
            LineaRecepcion linea = repository.buscarLineaRecepcion(
                            orden.id(), solicitada.ordenCompraLineaId())
                    .orElseThrow(() -> error(
                            HttpStatus.BAD_REQUEST,
                            "error.inventory.receipt.line-not-found"));
            if (solicitada.cantidad() > linea.cantidadPedida() - linea.cantidadRecibida()) {
                throw error(HttpStatus.CONFLICT, "error.inventory.receipt.quantity");
            }
            BigDecimal costoBase = linea.costoUnitario()
                    .multiply(solicitud.tipoCambioAplicado())
                    .setScale(decimalesBase, RoundingMode.HALF_UP)
                    .setScale(4, RoundingMode.UNNECESSARY);
            if (costoBase.precision() > 19) {
                throw error(HttpStatus.BAD_REQUEST, "error.inventory.receipt.cost-overflow");
            }
            lineas.add(new LineaRecibida(solicitada, linea, costoBase));
        }
        lineas.sort(Comparator.comparing(linea -> linea.orden().productoId()));

        UUID entradaId = UUID.randomUUID();
        OffsetDateTime ahora = ahora();
        EntradaInventario entrada = new EntradaInventario(
                entradaId,
                orden.id(),
                orden.numero(),
                solicitud.bodegaId(),
                solicitud.numeroDocumento().trim(),
                solicitud.tipoCambioAplicado(),
                actorId,
                ahora);
        repository.crearEntrada(entrada);
        for (LineaRecibida linea : lineas) {
            int actual = repository.bloquearSaldo(
                    linea.orden().productoId(), solicitud.bodegaId(), ahora);
            int nuevo;
            try {
                nuevo = Math.addExact(actual, linea.solicitud().cantidad());
            } catch (ArithmeticException exception) {
                throw error(HttpStatus.CONFLICT, "error.inventory.stock.overflow");
            }
            repository.actualizarSaldo(
                    linea.orden().productoId(), solicitud.bodegaId(), nuevo, ahora);
            repository.agregarLineaEntrada(
                    UUID.randomUUID(),
                    entradaId,
                    linea.orden().id(),
                    linea.orden().productoId(),
                    linea.solicitud().cantidad(),
                    linea.orden().costoUnitario(),
                    linea.costoBase());
            repository.crearMovimiento(new MovimientoInventario(
                    UUID.randomUUID(),
                    linea.orden().productoId(),
                    null,
                    solicitud.bodegaId(),
                    null,
                    "ENTRADA",
                    linea.solicitud().cantidad(),
                    "ENTRADA",
                    entradaId,
                    actorId,
                    ahora,
                    "Documento: " + entrada.numeroDocumento()));
        }
        repository.actualizarEstadoOrden(
                orden.id(),
                repository.tieneLineasPendientes(orden.id())
                        ? "RECIBIDA_PARCIAL"
                        : "RECIBIDA_COMPLETA");
        DetalleEntradaInventario resultado = detalleEntrada(entradaId, paisesPermitidos);
        auditoria.registrar(
                actorId, null, "RECIBIR", "entrada_inventario",
                entradaId, null, resultado);
        return resultado;
    }

    @Transactional(readOnly = true)
    public DetalleEntradaInventario obtenerEntrada(
            UUID id,
            Set<String> paisesPermitidos) {
        return detalleEntrada(id, paisesPermitidos);
    }

    private DetalleEntradaInventario detalleEntrada(
            UUID id,
            Set<String> paisesPermitidos) {
        EntradaInventario entrada = repository.buscarEntrada(id)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "error.inventory.receipt.not-found"));
        OrdenCompra orden = ordenExistente(entrada.ordenCompraId());
        validarAccesoPais(orden.paisId(), paisesPermitidos);
        return new DetalleEntradaInventario(
                entrada, repository.listarLineasEntrada(id));
    }

    private OrdenCompra ordenExistente(UUID id) {
        return repository.buscarOrden(id)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "error.inventory.order.not-found"));
    }

    private void validarAccesoPais(UUID paisId, Set<String> paisesPermitidos) {
        Pais pais = paisId == null
                ? null
                : configuracion.buscarPais(paisId)
                        .orElseThrow(() -> new IllegalStateException(
                                "La operación de compra referencia un país que no existe."));
        accesoPais.validar(paisesPermitidos, pais);
    }

    private void validarTipoCambio(UUID monedaId, BigDecimal tipoCambio) {
        String base = monedaBaseCodigo();
        String codigo = repository.codigoMoneda(monedaId);
        if (codigo == null) {
            throw error(HttpStatus.NOT_FOUND, "error.moneda.not-found");
        }
        if (codigo.equalsIgnoreCase(base) && tipoCambio.compareTo(BigDecimal.ONE) != 0) {
            throw error(HttpStatus.BAD_REQUEST, "error.inventory.exchange.base-currency");
        }
    }

    private String monedaBaseCodigo() {
        return parametros.resolver("moneda.base_codigo_iso", null)
                .valor()
                .asText();
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ErrorAplicacionException error(HttpStatus status, String messageKey) {
        return new ErrorAplicacionException(status, messageKey);
    }

    private record LineaRecibida(
            SolicitudLineaEntradaInventario solicitud,
            LineaRecepcion orden,
            BigDecimal costoBase) {
    }
}
