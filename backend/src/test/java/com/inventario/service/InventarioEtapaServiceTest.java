package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.domain.AjusteInventario;
import com.inventario.domain.Bodega;
import com.inventario.domain.DetalleEntradaInventario;
import com.inventario.domain.EntradaInventario;
import com.inventario.domain.EntradaInventarioLinea;
import com.inventario.domain.LineaRecepcion;
import com.inventario.domain.MovimientoInventario;
import com.inventario.domain.OrdenCompra;
import com.inventario.domain.ParametroResuelto;
import com.inventario.dto.SolicitudEntradaInventario;
import com.inventario.dto.SolicitudLineaEntradaInventario;
import com.inventario.dto.SolicitudSalidaInventario;
import com.inventario.repository.ConfiguracionRepository;
import com.inventario.repository.InventarioRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
class InventarioEtapaServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock
    private InventarioRepository repository;

    @Mock
    private AuditoriaService auditoria;

    @Mock
    private ConfiguracionRepository configuracion;

    @Mock
    private ParametroService parametros;

    private InventarioService inventario;
    private ComprasService compras;
    private AjustesService ajustes;
    private UUID actorId;
    private UUID productoId;
    private UUID bodegaId;
    private UUID proveedorId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AccesoPaisService accesoPais = new AccesoPaisService();
        inventario = new InventarioService(
                repository, configuracion, auditoria, accesoPais, clock);
        compras = new ComprasService(
                repository, configuracion, auditoria, accesoPais, parametros, clock);
        ajustes = new AjustesService(
                repository, configuracion, auditoria, accesoPais, clock);
        actorId = UUID.randomUUID();
        productoId = UUID.randomUUID();
        bodegaId = UUID.randomUUID();
        proveedorId = UUID.randomUUID();
    }

    @Test
    void registraSalidaConProveedorOficialYRestaSaldoDeFormaAtomica() {
        when(repository.productoExiste(productoId)).thenReturn(true);
        when(repository.buscarBodega(bodegaId))
                .thenReturn(Optional.of(
                        new Bodega(bodegaId, "GENERAL", "General", null, null, true)));
        when(repository.proveedorProducto(proveedorId, productoId)).thenReturn(true);
        when(repository.bloquearSaldo(eq(productoId), eq(bodegaId), any())).thenReturn(5);
        AtomicReference<MovimientoInventario> movimientoGuardado = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            movimientoGuardado.set(invocation.getArgument(0));
            return null;
        }).when(repository).crearMovimiento(any());
        when(repository.buscarMovimiento(any()))
                .thenAnswer(invocation -> Optional.of(movimientoGuardado.get()));

        MovimientoInventario salida = inventario.registrarSalida(
                new SolicitudSalidaInventario(
                        productoId,
                        bodegaId,
                        "DAÑO",
                        2,
                        proveedorId,
                        "REP-204",
                        "Caja averiada"),
                actorId,
                Set.of());

        assertThat(salida.tipo()).isEqualTo("SALIDA_DANO");
        assertThat(salida.referenciaTipo()).isEqualTo("PROVEEDOR");
        assertThat(salida.referenciaId()).isEqualTo(proveedorId);
        assertThat(salida.observacion()).contains("REP-204");
        verify(repository).actualizarSaldo(eq(productoId), eq(bodegaId), eq(3), any());
        verify(auditoria).registrar(
                eq(actorId), eq(null), eq("SALIDA"), eq("movimiento_inventario"),
                eq(salida.id()), eq(null), eq(salida));
    }

    @Test
    void rechazaSalidaQueSuperaExistenciasSinEscribirMovimiento() {
        when(repository.productoExiste(productoId)).thenReturn(true);
        when(repository.buscarBodega(bodegaId))
                .thenReturn(Optional.of(
                        new Bodega(bodegaId, "GENERAL", "General", null, null, true)));
        when(repository.proveedorProducto(proveedorId, productoId)).thenReturn(true);
        when(repository.bloquearSaldo(eq(productoId), eq(bodegaId), any())).thenReturn(1);

        assertThatThrownBy(() -> inventario.registrarSalida(
                new SolicitudSalidaInventario(
                        productoId, bodegaId, "ROBO", 2, proveedorId, "REP-205", null),
                actorId,
                Set.of()))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> {
                            assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(exception.messageKey())
                                    .isEqualTo("error.inventory.stock.insufficient");
                        });

        verify(repository, never()).actualizarSaldo(any(), any(), anyInt(), any());
        verify(repository, never()).crearMovimiento(any());
    }

    @Test
    void recibeParcialmenteYConvierteElCostoAMonedaBase() {
        UUID ordenId = UUID.randomUUID();
        UUID lineaId = UUID.randomUUID();
        OrdenCompra orden = new OrdenCompra(
                ordenId,
                "OC-1",
                proveedorId,
                "Proveedor",
                null,
                UUID.randomUUID(),
                new BigDecimal("2.00000000"),
                "ENVIADA_AL_PROVEEDOR",
                actorId,
                java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        when(repository.buscarOrden(ordenId)).thenReturn(Optional.of(orden));
        when(repository.buscarBodega(bodegaId))
                .thenReturn(Optional.of(
                        new Bodega(bodegaId, "GENERAL", "General", null, null, true)));
        configurarMonedaBase();
        when(repository.bodegaActiva(bodegaId)).thenReturn(true);
        when(repository.codigoMoneda(orden.monedaId())).thenReturn("USD");
        when(repository.decimalesMoneda("COP")).thenReturn(2);
        when(repository.documentoEntradaExiste(ordenId, "FAC-10")).thenReturn(false);
        when(repository.buscarLineaRecepcion(ordenId, lineaId))
                .thenReturn(Optional.of(new LineaRecepcion(
                        lineaId,
                        productoId,
                        5,
                        0,
                        new BigDecimal("10.0000"))));
        when(repository.bloquearSaldo(eq(productoId), eq(bodegaId), any())).thenReturn(0);
        when(repository.tieneLineasPendientes(ordenId)).thenReturn(true);
        when(repository.buscarEntrada(any()))
                .thenAnswer(invocation -> Optional.of(new EntradaInventario(
                        invocation.getArgument(0),
                        ordenId,
                        "OC-1",
                        bodegaId,
                        "FAC-10",
                        new BigDecimal("2.00000000"),
                        actorId,
                        java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))));
        when(repository.listarLineasEntrada(any()))
                .thenReturn(List.of(new EntradaInventarioLinea(
                        UUID.randomUUID(),
                        lineaId,
                        productoId,
                        "PRD-1",
                        2,
                        new BigDecimal("10.0000"),
                        new BigDecimal("20.0000"))));

        DetalleEntradaInventario resultado = compras.recibir(
                new SolicitudEntradaInventario(
                        ordenId,
                        bodegaId,
                        "FAC-10",
                        new BigDecimal("2.00000000"),
                        List.of(new SolicitudLineaEntradaInventario(lineaId, 2))),
                actorId,
                Set.of());

        assertThat(resultado.entrada().numeroDocumento()).isEqualTo("FAC-10");
        verify(repository).actualizarSaldo(eq(productoId), eq(bodegaId), eq(2), any());
        verify(repository).agregarLineaEntrada(
                any(), eq(resultado.entrada().id()), eq(lineaId), eq(productoId),
                eq(2), eq(new BigDecimal("10.0000")), eq(new BigDecimal("20.0000")));
        verify(repository).actualizarEstadoOrden(ordenId, "RECIBIDA_PARCIAL");
        verify(repository).crearMovimiento(any());
    }

    @Test
    void noPermiteRecibirMasDeLaCantidadPendiente() {
        UUID ordenId = UUID.randomUUID();
        UUID lineaId = UUID.randomUUID();
        OrdenCompra orden = new OrdenCompra(
                ordenId,
                "OC-2",
                proveedorId,
                "Proveedor",
                null,
                UUID.randomUUID(),
                BigDecimal.ONE,
                "ENVIADA_AL_PROVEEDOR",
                actorId,
                java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        when(repository.buscarOrden(ordenId)).thenReturn(Optional.of(orden));
        when(repository.buscarBodega(bodegaId))
                .thenReturn(Optional.of(
                        new Bodega(bodegaId, "GENERAL", "General", null, null, true)));
        configurarMonedaBase();
        when(repository.bodegaActiva(bodegaId)).thenReturn(true);
        when(repository.codigoMoneda(orden.monedaId())).thenReturn("COP");
        when(repository.decimalesMoneda("COP")).thenReturn(2);
        when(repository.documentoEntradaExiste(ordenId, "FAC-11")).thenReturn(false);
        when(repository.buscarLineaRecepcion(ordenId, lineaId))
                .thenReturn(Optional.of(new LineaRecepcion(
                        lineaId,
                        productoId,
                        5,
                        3,
                        new BigDecimal("10.0000"))));

        assertThatThrownBy(() -> compras.recibir(
                new SolicitudEntradaInventario(
                        ordenId,
                        bodegaId,
                        "FAC-11",
                        BigDecimal.ONE,
                        List.of(new SolicitudLineaEntradaInventario(lineaId, 3))),
                actorId,
                Set.of()))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.messageKey())
                                .isEqualTo("error.inventory.receipt.quantity"));

        verify(repository, never()).crearEntrada(any());
        verify(repository, never()).actualizarSaldo(any(), any(), anyInt(), any());
    }

    @Test
    void aplicaAjusteSoloDespuesDeAprobacionYRegistraMovimiento() {
        UUID ajusteId = UUID.randomUUID();
        AjusteInventario aprobado = new AjusteInventario(
                ajusteId,
                productoId,
                "PRD-1",
                bodegaId,
                "General",
                "OPERATIVO",
                5,
                3,
                -2,
                "Conteo físico",
                "APROBADO",
                actorId,
                UUID.randomUUID(),
                java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                null);
        AjusteInventario aplicado = new AjusteInventario(
                ajusteId,
                productoId,
                "PRD-1",
                bodegaId,
                "General",
                "OPERATIVO",
                5,
                3,
                -2,
                "Conteo físico",
                "APLICADO",
                actorId,
                aprobado.aprobadoPorId(),
                aprobado.solicitadoEnUtc(),
                aprobado.aprobadoEnUtc(),
                java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        when(repository.buscarAjuste(ajusteId))
                .thenReturn(Optional.of(aprobado), Optional.of(aplicado));
        when(repository.buscarBodega(bodegaId))
                .thenReturn(Optional.of(
                        new Bodega(bodegaId, "GENERAL", "General", null, null, true)));
        when(repository.bloquearSaldo(eq(productoId), eq(bodegaId), any())).thenReturn(5);
        when(repository.actualizarAjusteAplicado(eq(ajusteId), any())).thenReturn(true);

        AjusteInventario resultado = ajustes.aplicar(ajusteId, actorId, Set.of());

        assertThat(resultado.estado()).isEqualTo("APLICADO");
        verify(repository).actualizarSaldo(eq(productoId), eq(bodegaId), eq(3), any());
        verify(repository).crearMovimiento(org.mockito.ArgumentMatchers.argThat(movimiento ->
                movimiento.tipo().equals("AJUSTE")
                        && movimiento.cantidad() == 2
                        && movimiento.referenciaId().equals(ajusteId)));
    }

    @Test
    void exigeRevalidacionCuandoElSaldoCambioTrasLaAprobacion() {
        UUID ajusteId = UUID.randomUUID();
        when(repository.buscarAjuste(ajusteId))
                .thenReturn(Optional.of(new AjusteInventario(
                        ajusteId,
                        productoId,
                        "PRD-1",
                        bodegaId,
                        "General",
                        "OPERATIVO",
                        5,
                        3,
                        -2,
                        "Conteo",
                        "APROBADO",
                        actorId,
                        UUID.randomUUID(),
                        java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                        java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                        null)));
        when(repository.buscarBodega(bodegaId))
                .thenReturn(Optional.of(
                        new Bodega(bodegaId, "GENERAL", "General", null, null, true)));
        when(repository.bloquearSaldo(eq(productoId), eq(bodegaId), any())).thenReturn(4);

        assertThatThrownBy(() -> ajustes.aplicar(ajusteId, actorId, Set.of()))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.messageKey())
                                .isEqualTo("error.inventory.adjustment.stock-changed"));

        verify(repository, never()).actualizarSaldo(any(), any(), anyInt(), any());
        verify(repository, never()).crearMovimiento(any());
        verify(repository, never()).actualizarAjusteAplicado(any(), any());
    }

    private void configurarMonedaBase() {
        try {
            when(parametros.resolver("moneda.base_codigo_iso", null))
                    .thenReturn(new ParametroResuelto(
                            "moneda.base_codigo_iso",
                            new ObjectMapper().readTree("\"COP\""),
                            "TEXTO",
                            null,
                            null,
                            null,
                            "GLOBAL",
                            null,
                            LocalDate.of(2026, 1, 1),
                            null));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
