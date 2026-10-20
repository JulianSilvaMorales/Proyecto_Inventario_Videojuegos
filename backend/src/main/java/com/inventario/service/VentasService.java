package com.inventario.service;

import com.inventario.domain.CalculoImpuesto;
import com.inventario.domain.ConversionMoneda;
import com.inventario.domain.Bodega;
import com.inventario.domain.Moneda;
import com.inventario.domain.Pais;
import com.inventario.domain.ProductoVenta;
import com.inventario.domain.Venta;
import com.inventario.domain.VentaLinea;
import com.inventario.dto.RespuestaVenta;
import com.inventario.dto.SolicitudLineaVenta;
import com.inventario.dto.SolicitudVenta;
import com.inventario.repository.ConfiguracionRepository;
import com.inventario.repository.InventarioRepository;
import com.inventario.repository.VentasRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VentasService {

    private static final int RATE_SCALE = 8;

    private final VentasRepository repository;
    private final ConfiguracionRepository configuracion;
    private final InventarioRepository inventario;
    private final ImpuestoService impuestos;
    private final ConversionMonedaService conversiones;
    private final ParametroService parametros;
    private final AuditoriaService auditoria;
    private final AccesoPaisService accesoPais;
    private final Clock clock;

    public VentasService(
            VentasRepository repository,
            ConfiguracionRepository configuracion,
            InventarioRepository inventario,
            ImpuestoService impuestos,
            ConversionMonedaService conversiones,
            ParametroService parametros,
            AuditoriaService auditoria,
            AccesoPaisService accesoPais,
            Clock clock) {
        this.repository = repository;
        this.configuracion = configuracion;
        this.inventario = inventario;
        this.impuestos = impuestos;
        this.conversiones = conversiones;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.accesoPais = accesoPais;
        this.clock = clock;
    }

    @Transactional
    public RespuestaVenta crear(
            SolicitudVenta solicitud,
            UUID actorId,
            Set<String> paisesPermitidos) {
        if (solicitud.lineas() == null || solicitud.lineas().isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "error.sales.lines.required");
        }

        Pais pais = configuracion.buscarPais(solicitud.paisId())
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND, "error.sales.country.not-found"));
        if (!pais.habilitado()) {
            throw error(HttpStatus.CONFLICT, "error.sales.country.disabled");
        }
        accesoPais.validar(paisesPermitidos, pais.codigoIso());
        Moneda moneda = configuracion.buscarMoneda(solicitud.monedaId())
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND, "error.moneda.not-found"));
        if (!moneda.id().equals(pais.monedaLocalId())) {
            throw error(HttpStatus.BAD_REQUEST, "error.sales.currency.country");
        }
        validarCliente(solicitud.clienteId(), pais.id());

        OffsetDateTime ahora = ahora();
        LocalDate fechaVenta = ahora.toInstant()
                .atZone(ZoneId.of(pais.zonaHoraria()))
                .toLocalDate();
        UUID ventaId = UUID.randomUUID();
        Map<ClaveLinea, Integer> cantidades = consolidarLineas(solicitud.lineas());
        List<VentaLinea> lineas = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalImpuestos = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        RoundingMode modoRedondeo = modoRedondeo(moneda);

        for (Map.Entry<ClaveLinea, Integer> entrada : cantidades.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ClaveLinea::productoId)
                                .thenComparing(ClaveLinea::bodegaId)))
                .toList()) {
            ClaveLinea clave = entrada.getKey();
            int cantidad = entrada.getValue();
            ProductoVenta producto = validarProducto(clave.productoId());
            validarBodega(clave.bodegaId(), pais.id());
            BigDecimal precioUnitario = repository.buscarPrecioVigente(
                            producto.id(), moneda.id(), fechaVenta)
                    .orElseThrow(() -> error(
                            HttpStatus.NOT_FOUND, "error.catalog.price.not-found"))
                    .setScale(moneda.decimales(), modoRedondeo);
            BigDecimal importeProductos = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
            CalculoImpuesto calculo = impuestos.calcular(
                    pais.id(),
                    producto.categoriaId(),
                    moneda.id(),
                    importeProductos,
                    fechaVenta);

            int disponible = inventario.cantidadDisponible(producto.id(), clave.bodegaId());
            if (disponible < cantidad) {
                throw error(HttpStatus.CONFLICT, "error.sales.stock.insufficient");
            }

            lineas.add(new VentaLinea(
                    UUID.randomUUID(),
                    ventaId,
                    producto.id(),
                    producto.codigoInterno(),
                    producto.codigoInterno(),
                    clave.bodegaId(),
                    null,
                    cantidad,
                    precioUnitario,
                    calculo.impuestoId(),
                    calculo.nombreImpuesto(),
                    calculo.tasa(),
                    calculo.importeImpuesto(),
                    calculo.baseGravable(),
                    calculo.total()));
            subtotal = subtotal.add(calculo.baseGravable());
            totalImpuestos = totalImpuestos.add(calculo.importeImpuesto());
            total = total.add(calculo.total());
        }

        Moneda monedaBase = monedaBase();
        ConversionMoneda conversion = conversiones.convertir(
                moneda.id(), monedaBase.id(), total, ahora.toInstant());
        BigDecimal tasaAplicada =
                conversion.tasaAplicada().setScale(RATE_SCALE, modoRedondeo(monedaBase));
        BigDecimal totalBase = total.multiply(tasaAplicada)
                .setScale(monedaBase.decimales(), modoRedondeo(monedaBase));
        Venta nueva = new Venta(
                ventaId,
                "VTA-" + ventaId.toString().replace("-", "").toUpperCase(Locale.ROOT),
                solicitud.clienteId(),
                pais.id(),
                pais.codigoIso(),
                moneda.id(),
                moneda.codigoIso(),
                tasaAplicada,
                subtotal,
                totalImpuestos,
                total,
                totalBase,
                "PENDIENTE_DE_PAGO",
                "SIN_PAGO",
                null,
                actorId,
                ahora,
                null,
                null,
                null);
        repository.crearVenta(nueva, lineas);
        RespuestaVenta creada = obtener(ventaId, paisesPermitidos);
        auditoria.registrar(
                actorId, pais.id(), "CREAR", "venta", ventaId, null, creada);
        return creada;
    }

    @Transactional
    public RespuestaVenta confirmar(
            UUID id,
            String medioPago,
            UUID actorId,
            Set<String> paisesPermitidos) {
        if (!repository.bloquearVenta(id)) {
            throw error(HttpStatus.NOT_FOUND, "error.sales.not-found");
        }
        Venta venta = obtenerCabecera(id);
        accesoPais.validar(paisesPermitidos, venta.paisCodigo());
        if (!venta.estado().equals("PENDIENTE_DE_PAGO")
                || !venta.estadoPago().equals("SIN_PAGO")) {
            throw error(HttpStatus.CONFLICT, "error.sales.transition");
        }
        String medioNormalizado = normalizarMedioPago(medioPago);
        OffsetDateTime ahora = ahora();
        List<VentaLinea> lineas = repository.listarLineas(id).stream()
                .sorted(Comparator.comparing(VentaLinea::productoId)
                        .thenComparing(VentaLinea::bodegaId))
                .toList();

        for (VentaLinea linea : lineas) {
            validarProducto(linea.productoId());
            validarBodega(linea.bodegaId(), venta.paisId());
            int disponible = inventario.bloquearSaldo(
                    linea.productoId(), linea.bodegaId(), ahora);
            if (disponible < linea.cantidad()) {
                throw error(HttpStatus.CONFLICT, "error.sales.stock.insufficient");
            }
        }
        for (VentaLinea linea : lineas) {
            int disponible = inventario.bloquearSaldo(
                    linea.productoId(), linea.bodegaId(), ahora);
            inventario.actualizarSaldo(
                    linea.productoId(),
                    linea.bodegaId(),
                    disponible - linea.cantidad(),
                    ahora);
            inventario.crearMovimiento(new com.inventario.domain.MovimientoInventario(
                    UUID.randomUUID(),
                    linea.productoId(),
                    linea.codigoProducto(),
                    linea.bodegaId(),
                    linea.bodegaNombre(),
                    "SALIDA_VENTA",
                    linea.cantidad(),
                    "VENTA",
                    venta.id(),
                    actorId,
                    ahora,
                    "Venta " + venta.numero()));
        }
        if (!repository.confirmarVenta(id, medioNormalizado, ahora)) {
            throw error(HttpStatus.CONFLICT, "error.sales.transition");
        }

        RespuestaVenta confirmada = obtener(id, paisesPermitidos);
        auditoria.registrar(
                actorId, venta.paisId(), "CONFIRMAR", "venta", id, venta, confirmada);
        return confirmada;
    }

    @Transactional
    public RespuestaVenta anular(
            UUID id,
            UUID actorId,
            Set<String> paisesPermitidos) {
        if (!repository.bloquearVenta(id)) {
            throw error(HttpStatus.NOT_FOUND, "error.sales.not-found");
        }
        Venta anterior = obtenerCabecera(id);
        accesoPais.validar(paisesPermitidos, anterior.paisCodigo());
        if (!anterior.estado().equals("PENDIENTE_DE_PAGO")
                || !anterior.estadoPago().equals("SIN_PAGO")) {
            throw error(HttpStatus.CONFLICT, "error.sales.transition");
        }
        if (!repository.anularVenta(id, ahora())) {
            throw error(HttpStatus.CONFLICT, "error.sales.transition");
        }
        RespuestaVenta anulada = obtener(id, paisesPermitidos);
        auditoria.registrar(
                actorId, anterior.paisId(), "ANULAR", "venta", id, anterior, anulada);
        return anulada;
    }

    @Transactional(readOnly = true)
    public RespuestaVenta obtener(UUID id, Set<String> paisesPermitidos) {
        Venta venta = obtenerCabecera(id);
        accesoPais.validar(paisesPermitidos, venta.paisCodigo());
        return new RespuestaVenta(
                venta,
                repository.listarLineas(id),
                venta.estado().equals("CONFIRMADA"));
    }

    @Transactional(readOnly = true)
    public RespuestaVenta obtenerRecibo(UUID id, Set<String> paisesPermitidos) {
        RespuestaVenta venta = obtener(id, paisesPermitidos);
        if (!venta.reciboSimulado()) {
            throw error(HttpStatus.CONFLICT, "error.sales.receipt.pending");
        }
        return venta;
    }

    private Venta obtenerCabecera(UUID id) {
        return repository.buscarVenta(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "error.sales.not-found"));
    }

    private void validarCliente(UUID clienteId, UUID paisId) {
        if (clienteId != null && !repository.clienteActivoEnPais(clienteId, paisId)) {
            throw error(HttpStatus.BAD_REQUEST, "error.sales.customer.invalid");
        }
    }

    private void validarBodega(UUID bodegaId, UUID paisId) {
        inventario.bloquearBodega(bodegaId);
        Bodega bodega = inventario.buscarBodega(bodegaId)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND, "error.inventory.warehouse.not-found"));
        if (!bodega.activo()) {
            throw error(HttpStatus.CONFLICT, "error.inventory.warehouse.inactive");
        }
        if (bodega.paisId() == null) {
            throw error(HttpStatus.CONFLICT, "error.inventory.warehouse.country.required");
        }
        if (!bodega.paisId().equals(paisId)) {
            throw error(HttpStatus.CONFLICT, "error.sales.warehouse.country");
        }
    }

    private ProductoVenta validarProducto(UUID productoId) {
        if (!repository.bloquearProducto(productoId)) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.product.not-found");
        }
        ProductoVenta producto = repository.buscarProducto(productoId)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND, "error.catalog.product.not-found"));
        if (!producto.activo()) {
            throw error(HttpStatus.CONFLICT, "error.catalog.product.inactive");
        }
        return producto;
    }

    private Map<ClaveLinea, Integer> consolidarLineas(List<SolicitudLineaVenta> lineas) {
        Map<ClaveLinea, Integer> cantidades = new LinkedHashMap<>();
        try {
            for (SolicitudLineaVenta linea : lineas) {
                if (linea == null || linea.productoId() == null || linea.bodegaId() == null
                        || linea.cantidad() < 1) {
                    throw error(HttpStatus.BAD_REQUEST, "error.sales.lines.invalid");
                }
                ClaveLinea clave = new ClaveLinea(linea.productoId(), linea.bodegaId());
                cantidades.merge(clave, linea.cantidad(), Math::addExact);
            }
        } catch (ArithmeticException exception) {
            throw error(HttpStatus.BAD_REQUEST, "error.sales.quantity.overflow");
        }
        if (cantidades.size() > 100) {
            throw error(HttpStatus.BAD_REQUEST, "error.sales.lines.limit");
        }
        return cantidades;
    }

    private Moneda monedaBase() {
        String codigo = parametros.resolver("moneda.base_codigo_iso", null)
                .valor()
                .asText();
        return configuracion.buscarMonedaPorCodigo(codigo)
                .orElseThrow(() -> new IllegalStateException(
                        "La moneda base configurada no existe en el catálogo de monedas."));
    }

    private String normalizarMedioPago(String medioPago) {
        if (medioPago == null || medioPago.isBlank() || medioPago.trim().length() > 40) {
            throw error(HttpStatus.BAD_REQUEST, "error.sales.payment-method.required");
        }
        return medioPago.trim().toUpperCase(Locale.ROOT);
    }

    private RoundingMode modoRedondeo(Moneda moneda) {
        try {
            return RoundingMode.valueOf(moneda.reglaRedondeo());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "La moneda " + moneda.codigoIso() + " tiene una regla de redondeo inválida.",
                    exception);
        }
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ErrorAplicacionException error(HttpStatus status, String messageKey) {
        return new ErrorAplicacionException(status, messageKey);
    }

    private record ClaveLinea(UUID productoId, UUID bodegaId) {
    }
}
