package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inventario.domain.SaldoInventario;
import com.inventario.repository.ConfiguracionRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ReportesServiceTest {

    @Mock
    private InventarioService inventario;

    @Mock
    private ConfiguracionRepository configuracion;

    @Test
    void exportaExistenciasComoCsvYNeutralizaFormulas() throws Exception {
        SaldoInventario saldo = new SaldoInventario(
                UUID.randomUUID(),
                "PRD-1",
                "=1+1",
                UUID.randomUUID(),
                "BOD-1",
                "Bodega \"General\"",
                4,
                OffsetDateTime.parse("2026-10-20T12:00:00Z"));
        when(inventario.listarSaldos(
                        eq(saldo.productoId()),
                        eq(saldo.bodegaId()),
                        eq(null),
                        eq(Set.of()),
                        eq(false),
                        any()))
                .thenReturn(new PageImpl<>(
                        List.of(saldo),
                        PageRequest.of(0, 100),
                        1));
        ReportesService reportes = new ReportesService(
                inventario, configuracion, new AccesoPaisService());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        reportes.exportarExistencias(
                saldo.productoId(), saldo.bodegaId(), null, Set.of(), false, output);

        String csv = output.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("\"'=1+1\"");
        assertThat(csv).contains("\"Bodega \"\"General\"\"\"");
        assertThat(csv).contains("\"4\"");
    }

    @Test
    void reenviaFiltrosDePaisYMonedaAlReporteDeMovimientos() {
        UUID paisId = UUID.randomUUID();
        UUID monedaId = UUID.randomUUID();
        OffsetDateTime desde = OffsetDateTime.parse("2026-10-01T00:00:00Z");
        OffsetDateTime hasta = OffsetDateTime.parse("2026-10-31T23:59:59Z");
        PageRequest pageable = PageRequest.of(0, 20);
        when(inventario.listarMovimientos(
                        null,
                        null,
                        null,
                        "SALIDA_VENTA",
                        desde,
                        hasta,
                        paisId,
                        monedaId,
                        Set.of(),
                        pageable))
                .thenReturn(Page.empty(pageable));
        ReportesService reportes = new ReportesService(
                inventario, configuracion, new AccesoPaisService());

        reportes.listarMovimientos(
                null,
                null,
                null,
                "SALIDA_VENTA",
                desde,
                hasta,
                paisId,
                monedaId,
                Set.of(),
                pageable);

        verify(inventario).listarMovimientos(
                null,
                null,
                null,
                "SALIDA_VENTA",
                desde,
                hasta,
                paisId,
                monedaId,
                Set.of(),
                pageable);
    }
}
