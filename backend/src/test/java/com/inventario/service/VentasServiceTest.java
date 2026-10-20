package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.TextNode;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class VentasServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final UUID PAIS_ID = UUID.randomUUID();
    private static final UUID MONEDA_ID = UUID.randomUUID();
    private static final UUID PRODUCTO_ID = UUID.randomUUID();
    private static final UUID BODEGA_ID = UUID.randomUUID();
    private static final UUID CATEGORIA_ID = UUID.randomUUID();
    private static final UUID IMPUESTO_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Mock
    private VentasRepository repository;

    @Mock
    private ConfiguracionRepository configuracion;

    @Mock
    private InventarioRepository inventario;

    @Mock
    private ImpuestoService impuestos;

    @Mock
    private ConversionMonedaService conversiones;

    @Mock
    private ParametroService parametros;

    @Mock
    private AuditoriaService auditoria;

    private VentasService ventas;
    private final AtomicReference<Venta> guardada = new AtomicReference<>();
    private final AtomicReference<List<VentaLinea>> lineasGuardadas = new AtomicReference<>();
    private final Moneda moneda = new Moneda(
            MONEDA_ID, "COP", "Peso colombiano", "$", 2, "es-CO", "HALF_UP");

    @BeforeEach
    void setUp() {
        ventas = new VentasService(
                repository,
                configuracion,
                inventario,
                impuestos,
                conversiones,
                parametros,
                auditoria,
                new AccesoPaisService(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(configuracion.buscarPais(PAIS_ID)).thenReturn(Optional.of(new Pais(
                PAIS_ID, "CO", "Colombia", MONEDA_ID, null, "America/Bogota", true)));
        when(configuracion.buscarMoneda(MONEDA_ID)).thenReturn(Optional.of(moneda));
        when(configuracion.buscarMonedaPorCodigo("COP")).thenReturn(Optional.of(moneda));
        when(parametros.resolver("moneda.base_codigo_iso", null))
                .thenReturn(new com.inventario.domain.ParametroResuelto(
                        "moneda.base_codigo_iso",
                        TextNode.valueOf("COP"),
                        "TEXTO",
                        null,
                        null,
                        TextNode.valueOf("COP"),
                        "NEGOCIO",
                        null,
                        java.time.LocalDate.of(2026, 1, 1),
                        null));
        when(repository.buscarProducto(PRODUCTO_ID))
                .thenReturn(Optional.of(new ProductoVenta(
                        PRODUCTO_ID, "JUEGO-01", CATEGORIA_ID, true)));
        when(repository.bloquearProducto(PRODUCTO_ID)).thenReturn(true);
        when(repository.buscarPrecioVigente(
                        eq(PRODUCTO_ID), eq(MONEDA_ID), any()))
                .thenReturn(Optional.of(new BigDecimal("100.00")));
        when(inventario.buscarBodega(BODEGA_ID))
                .thenReturn(Optional.of(new Bodega(
                        BODEGA_ID, "GENERAL", "Bodega general", null, PAIS_ID, true)));
        when(inventario.cantidadDisponible(PRODUCTO_ID, BODEGA_ID))
                .thenReturn(5);
        when(impuestos.calcular(eq(PAIS_ID), eq(CATEGORIA_ID), eq(MONEDA_ID), any(), any()))
                .thenAnswer(invocation -> {
                    BigDecimal base = invocation.getArgument(3);
                    BigDecimal tax = base.multiply(new BigDecimal("0.19"))
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                    return new CalculoImpuesto(
                            IMPUESTO_ID,
                            "IVA",
                            new BigDecimal("0.19"),
                            false,
                            base,
                            base,
                            tax,
                            base.add(tax),
                            "COP",
                            java.time.LocalDate.of(2026, 10, 20));
                });
        when(conversiones.convertir(eq(MONEDA_ID), eq(MONEDA_ID), any(), any()))
                .thenAnswer(invocation -> new ConversionMoneda(
                        null,
                        invocation.getArgument(2),
                        "COP",
                        BigDecimal.ONE,
                        invocation.getArgument(2),
                        "COP",
                        NOW));
        when(repository.buscarVenta(any()))
                .thenAnswer(invocation -> Optional.ofNullable(guardada.get()));
        when(repository.listarLineas(any()))
                .thenAnswer(invocation -> lineasGuardadas.get() == null
                        ? List.of()
                        : lineasGuardadas.get());
        doAnswer(invocation -> {
                    guardada.set(invocation.getArgument(0));
                    lineasGuardadas.set(invocation.getArgument(1));
                    return null;
                })
                .when(repository)
                .crearVenta(any(), any());
    }

    @Test
    void creaVentaPendienteConImpuestosYNoDescuentaInventario() {
        RespuestaVenta respuesta = ventas.crear(solicitud(2), ACTOR_ID, Set.of("CO"));

        assertThat(respuesta.reciboSimulado()).isFalse();
        assertThat(respuesta.venta().estado()).isEqualTo("PENDIENTE_DE_PAGO");
        assertThat(respuesta.venta().estadoPago()).isEqualTo("SIN_PAGO");
        assertThat(respuesta.venta().subtotal()).isEqualByComparingTo("200.00");
        assertThat(respuesta.venta().totalImpuestos()).isEqualByComparingTo("38.00");
        assertThat(respuesta.venta().total()).isEqualByComparingTo("238.00");
        assertThat(respuesta.lineas()).hasSize(1);
        assertThatThrownBy(() -> ventas.obtenerRecibo(
                        respuesta.venta().id(), Set.of("CO")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> {
                            assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(exception.messageKey())
                                    .isEqualTo("error.sales.receipt.pending");
                        });
        verify(inventario, never()).actualizarSaldo(any(), any(), anyInt(), any());
        verify(inventario, never()).crearMovimiento(any());
        verify(inventario, never()).bloquearSaldo(any(), any(), any());
    }

    @Test
    void confirmarVentaDescuentaSaldoYRegistraMovimiento() {
        ventas.crear(solicitud(2), ACTOR_ID, Set.of("CO"));
        when(inventario.bloquearSaldo(eq(PRODUCTO_ID), eq(BODEGA_ID), any()))
                .thenReturn(5);
        when(repository.bloquearVenta(guardada.get().id())).thenReturn(true);
        when(repository.confirmarVenta(eq(guardada.get().id()), eq("EFECTIVO"), any()))
                .thenAnswer(invocation -> {
                    Venta anterior = guardada.get();
                    guardada.set(new Venta(
                            anterior.id(),
                            anterior.numero(),
                            anterior.clienteId(),
                            anterior.paisId(),
                            anterior.paisCodigo(),
                            anterior.monedaId(),
                            anterior.monedaCodigo(),
                            anterior.tipoCambioAplicado(),
                            anterior.subtotal(),
                            anterior.totalImpuestos(),
                            anterior.total(),
                            anterior.totalMonedaBase(),
                            "CONFIRMADA",
                            "PAGADO",
                            "EFECTIVO",
                            anterior.registradaPorId(),
                            anterior.creadaEnUtc(),
                            invocation.getArgument(2),
                            invocation.getArgument(2),
                            null));
                    return true;
                });

        RespuestaVenta confirmada = ventas.confirmar(
                guardada.get().id(), " efectivo ", ACTOR_ID, Set.of("CO"));

        assertThat(confirmada.venta().estado()).isEqualTo("CONFIRMADA");
        assertThat(confirmada.venta().estadoPago()).isEqualTo("PAGADO");
        assertThat(confirmada.reciboSimulado()).isTrue();
        verify(inventario).actualizarSaldo(
                eq(PRODUCTO_ID), eq(BODEGA_ID), eq(3), any());
        verify(inventario).crearMovimiento(org.mockito.ArgumentMatchers.argThat(movimiento ->
                movimiento.tipo().equals("SALIDA_VENTA")
                        && movimiento.referenciaTipo().equals("VENTA")
                        && movimiento.cantidad() == 2));
    }

    @Test
    void rechazaConfirmacionSinExistenciasSinEscribirMovimientos() {
        ventas.crear(solicitud(2), ACTOR_ID, Set.of("CO"));
        when(repository.bloquearVenta(guardada.get().id())).thenReturn(true);
        when(inventario.bloquearSaldo(eq(PRODUCTO_ID), eq(BODEGA_ID), any()))
                .thenReturn(1);

        assertThatThrownBy(() -> ventas.confirmar(
                        guardada.get().id(), "EFECTIVO", ACTOR_ID, Set.of("CO")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> {
                            assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(exception.messageKey())
                                    .isEqualTo("error.sales.stock.insufficient");
                        });

        verify(inventario, never()).actualizarSaldo(any(), any(), anyInt(), any());
        verify(inventario, never()).crearMovimiento(any());
        verify(repository, never()).confirmarVenta(any(), any(), any());
    }

    @Test
    void anulaSoloUnaVentaPendienteSinPago() {
        ventas.crear(solicitud(1), ACTOR_ID, Set.of("CO"));
        UUID ventaId = guardada.get().id();
        when(repository.bloquearVenta(ventaId)).thenReturn(true);
        when(repository.anularVenta(eq(ventaId), any())).thenAnswer(invocation -> {
            Venta anterior = guardada.get();
            guardada.set(new Venta(
                    anterior.id(),
                    anterior.numero(),
                    anterior.clienteId(),
                    anterior.paisId(),
                    anterior.paisCodigo(),
                    anterior.monedaId(),
                    anterior.monedaCodigo(),
                    anterior.tipoCambioAplicado(),
                    anterior.subtotal(),
                    anterior.totalImpuestos(),
                    anterior.total(),
                    anterior.totalMonedaBase(),
                    "ANULADA",
                    "SIN_PAGO",
                    null,
                    anterior.registradaPorId(),
                    anterior.creadaEnUtc(),
                    null,
                    null,
                    invocation.getArgument(1)));
            return true;
        });

        RespuestaVenta anulada = ventas.anular(ventaId, ACTOR_ID, Set.of("CO"));

        assertThat(anulada.venta().estado()).isEqualTo("ANULADA");
        verify(inventario, never()).actualizarSaldo(any(), any(), anyInt(), any());
    }

    private SolicitudVenta solicitud(int cantidad) {
        return new SolicitudVenta(
                null,
                PAIS_ID,
                MONEDA_ID,
                List.of(new SolicitudLineaVenta(PRODUCTO_ID, BODEGA_ID, cantidad)));
    }
}
