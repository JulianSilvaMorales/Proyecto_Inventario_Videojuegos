package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.inventario.domain.Moneda;
import com.inventario.domain.Pais;
import com.inventario.domain.ParametroResuelto;
import com.inventario.domain.Venta;
import com.inventario.dto.PaisVenta;
import com.inventario.repository.ConfiguracionRepository;
import com.inventario.repository.InventarioRepository;
import com.inventario.repository.VentasRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

class VentasListadoTest {

    private VentasRepository repository;
    private ConfiguracionRepository configuracion;
    private ParametroService parametros;
    private VentasService ventas;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(VentasRepository.class);
        configuracion = Mockito.mock(ConfiguracionRepository.class);
        parametros = Mockito.mock(ParametroService.class);
        ventas = new VentasService(
                repository,
                configuracion,
                Mockito.mock(InventarioRepository.class),
                Mockito.mock(ImpuestoService.class),
                Mockito.mock(ConversionMonedaService.class),
                parametros,
                Mockito.mock(AuditoriaService.class),
                new AccesoPaisService(),
                Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void normalizaEstadoYConservaAlcancePorPais() {
        PageRequest pagina = PageRequest.of(0, 10);
        Page<Venta> vacia = new PageImpl<>(List.of(), pagina, 0);
        when(repository.listar(eq("CONFIRMADA"), eq(Set.of("CO")), any())).thenReturn(vacia);

        assertThat(ventas.listar(" confirmada ", Set.of("CO"), pagina)).isSameAs(vacia);
    }

    @Test
    void sinEstadoListaTodasLasVentas() {
        PageRequest pagina = PageRequest.of(0, 10);
        when(repository.listar(eq(null), eq(Set.of()), any()))
                .thenReturn(new PageImpl<>(List.of(), pagina, 0));

        ventas.listar("", Set.of(), pagina);

        verify(repository).listar(null, Set.of(), pagina);
    }

    @Test
    void rechazaEstadoDesconocido() {
        assertThatThrownBy(() -> ventas.listar("BORRADOR", Set.of(), PageRequest.of(0, 10)))
                .isInstanceOfSatisfying(ErrorAplicacionException.class, error ->
                        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).listar(any(), any(), any());
    }

    @Test
    void paisesDisponiblesIncluyeMonedaLocalYMediosDePagoDelPais() {
        UUID cop = UUID.randomUUID();
        Pais colombia = new Pais(UUID.randomUUID(), "CO", "Colombia", cop, null, "America/Bogota", true);
        Pais estadosUnidos = new Pais(UUID.randomUUID(), "US", "United States", UUID.randomUUID(), null, null, true);
        Pais deshabilitado = new Pais(UUID.randomUUID(), "MX", "México", cop, null, null, false);
        when(configuracion.listarPaises(any(), any()))
                .thenReturn(new PageImpl<>(List.of(colombia, estadosUnidos, deshabilitado)));
        when(configuracion.buscarMoneda(cop)).thenReturn(Optional.of(
                new Moneda(cop, "COP", "Peso colombiano", "$", 2, "es-CO", "HALF_UP")));
        ArrayNode medios = new ObjectMapper().createArrayNode().add("efectivo").add("NEQUI");
        when(parametros.resolver("ventas.medios_pago", "CO")).thenReturn(new ParametroResuelto(
                "ventas.medios_pago", medios, "JSON", null, null, medios, "PAIS", "CO",
                LocalDate.of(2026, 1, 1), null));

        List<PaisVenta> paises = ventas.paisesDisponibles(Set.of("CO", "MX"));

        assertThat(paises).singleElement().satisfies(pais -> {
            assertThat(pais.paisCodigo()).isEqualTo("CO");
            assertThat(pais.monedaCodigo()).isEqualTo("COP");
            assertThat(pais.monedaDecimales()).isEqualTo(2);
            assertThat(pais.mediosPago()).containsExactly("EFECTIVO", "NEQUI");
        });
    }

    @Test
    void sinParametroDeMediosDePagoDevuelveListaVacia() {
        UUID usd = UUID.randomUUID();
        Pais estadosUnidos = new Pais(UUID.randomUUID(), "US", "United States", usd, null, null, true);
        when(configuracion.listarPaises(any(), any())).thenReturn(new PageImpl<>(List.of(estadosUnidos)));
        when(configuracion.buscarMoneda(usd)).thenReturn(Optional.of(
                new Moneda(usd, "USD", "US dollar", "$", 2, "en-US", "HALF_UP")));
        when(parametros.resolver("ventas.medios_pago", "US"))
                .thenThrow(new ParametroNoEncontradoException("ventas.medios_pago", "US"));

        assertThat(ventas.paisesDisponibles(Set.of()))
                .singleElement()
                .satisfies(pais -> assertThat(pais.mediosPago()).isEmpty());
    }
}
