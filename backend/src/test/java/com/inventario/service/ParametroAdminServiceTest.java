package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.domain.ParametroFila;
import com.inventario.dto.SolicitudActualizacionParametro;
import com.inventario.dto.SolicitudParametro;
import com.inventario.repository.ParametroAdminRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class ParametroAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final UUID ACTOR = UUID.randomUUID();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ParametroAdminRepository repository;
    private ParametroService parametrosResueltos;
    private AuditoriaService auditoria;
    private AccesoPaisService accesoPais;
    private ParametroAdminService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ParametroAdminRepository.class);
        parametrosResueltos = Mockito.mock(ParametroService.class);
        auditoria = Mockito.mock(AuditoriaService.class);
        accesoPais = Mockito.mock(AccesoPaisService.class);
        service = new ParametroAdminService(
                repository,
                parametrosResueltos,
                auditoria,
                accesoPais,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void creaParametroDeNegocioConValorPorDefectoIgualAlValor() {
        when(repository.buscarSuperpuesto(
                eq("sesion.inactividad_minutos"), eq("NEGOCIO"), isNull(),
                any(), any(), isNull()))
                .thenReturn(Optional.empty());
        UUID nuevoId = UUID.randomUUID();
        when(repository.crear(any(), eq(ACTOR), any())).thenReturn(nuevoId);
        when(repository.buscar(nuevoId)).thenReturn(Optional.of(fila(nuevoId, 60)));

        SolicitudParametro solicitud = new SolicitudParametro(
                "sesion.inactividad_minutos",
                60,
                "ENTERO",
                new BigDecimal("1"),
                new BigDecimal("1440"),
                "NEGOCIO",
                null,
                LocalDate.of(2026, 1, 1),
                null);

        ParametroFila creada = service.crear(solicitud, ACTOR, Set.of());

        assertThat(creada.id()).isEqualTo(nuevoId);
        assertThat(creada.valorPorDefecto().asInt()).isEqualTo(60);
        verify(parametrosResueltos).invalidarTodo();
        verify(auditoria).registrar(any(), isNull(), eq("CREAR"), eq("parametro"),
                eq(nuevoId), isNull(), eq(creada));
    }

    @Test
    void rechazaValorEnteroNoEntero() {
        SolicitudParametro solicitud = new SolicitudParametro(
                "sesion.inactividad_minutos",
                60.5,
                "ENTERO",
                null,
                null,
                "NEGOCIO",
                null,
                LocalDate.of(2026, 1, 1),
                null);

        assertThatThrownBy(() -> service.crear(solicitud, ACTOR, Set.of()))
                .isInstanceOfSatisfying(ErrorAplicacionException.class, error ->
                        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).crear(any(), any(), any());
    }

    @Test
    void rechazaValorFueraDeRango() {
        SolicitudParametro solicitud = new SolicitudParametro(
                "sesion.inactividad_minutos",
                5000,
                "ENTERO",
                new BigDecimal("1"),
                new BigDecimal("1440"),
                "NEGOCIO",
                null,
                LocalDate.of(2026, 1, 1),
                null);

        assertThatThrownBy(() -> service.crear(solicitud, ACTOR, Set.of()))
                .isInstanceOfSatisfying(ErrorAplicacionException.class, error ->
                        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).crear(any(), any(), any());
    }

    @Test
    void aceptaDecimalEnTextoSinPerderPrecisionYRechazaTextoNoNumerico() {
        UUID nuevoId = UUID.randomUUID();
        when(repository.buscarSuperpuesto(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.crear(any(), eq(ACTOR), any())).thenReturn(nuevoId);
        when(repository.buscar(nuevoId)).thenReturn(Optional.of(fila(nuevoId, 1)));

        service.crear(new SolicitudParametro(
                        "ventas.recargo", "0.1234567890123456789", "DECIMAL", null, null,
                        "NEGOCIO", null, LocalDate.of(2026, 1, 1), null),
                ACTOR,
                Set.of());

        ArgumentCaptor<ParametroFila> guardado =
                ArgumentCaptor.forClass(ParametroFila.class);
        verify(repository).crear(guardado.capture(), eq(ACTOR), any());
        assertThat(guardado.getValue().valor().isNumber()).isTrue();
        assertThat(guardado.getValue().valor().decimalValue())
                .isEqualByComparingTo("0.1234567890123456789");

        assertThatThrownBy(() -> service.crear(new SolicitudParametro(
                        "ventas.recargo", "diez", "DECIMAL", null, null,
                        "NEGOCIO", null, LocalDate.of(2026, 1, 1), null),
                ACTOR,
                Set.of()))
                .isInstanceOfSatisfying(ErrorAplicacionException.class, error ->
                        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rechazaParametroDePaisSinPais() {
        SolicitudParametro solicitud = new SolicitudParametro(
                "impuesto.general",
                new BigDecimal("0.19"),
                "DECIMAL",
                null,
                null,
                "PAIS",
                null,
                LocalDate.of(2026, 1, 1),
                null);

        assertThatThrownBy(() -> service.crear(solicitud, ACTOR, Set.of()))
                .isInstanceOfSatisfying(ErrorAplicacionException.class, error ->
                        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).crear(any(), any(), any());
    }

    @Test
    void rechazaVigenciaConFinAnteriorAlInicio() {
        SolicitudParametro solicitud = new SolicitudParametro(
                "impuesto.general",
                new BigDecimal("0.19"),
                "DECIMAL",
                null,
                null,
                "NEGOCIO",
                null,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> service.crear(solicitud, ACTOR, Set.of()))
                .isInstanceOfSatisfying(ErrorAplicacionException.class, error ->
                        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).crear(any(), any(), any());
    }

    @Test
    void rechazaCreacionQueSeSuperponeConVigenciaExistente() {
        when(repository.buscarSuperpuesto(
                eq("sesion.inactividad_minutos"), eq("NEGOCIO"), isNull(),
                any(), any(), isNull()))
                .thenReturn(Optional.of(fila(UUID.randomUUID(), 30)));

        SolicitudParametro solicitud = new SolicitudParametro(
                "sesion.inactividad_minutos",
                90,
                "ENTERO",
                null,
                null,
                "NEGOCIO",
                null,
                LocalDate.of(2026, 3, 1),
                null);

        assertThatThrownBy(() -> service.crear(solicitud, ACTOR, Set.of()))
                .isInstanceOfSatisfying(ErrorAplicacionException.class, error ->
                        assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).crear(any(), any(), any());
    }

    @Test
    void actualizaParametroYAuditaElCambio() throws Exception {
        UUID id = UUID.randomUUID();
        ParametroFila anterior = fila(id, 60);
        when(repository.buscar(id)).thenReturn(Optional.of(anterior));
        when(repository.buscarSuperpuesto(
                eq("sesion.inactividad_minutos"), eq("NEGOCIO"), isNull(),
                any(), any(), eq(id)))
                .thenReturn(Optional.empty());
        ParametroFila actualizada = fila(id, 90);
        when(repository.actualizar(eq(id), any(), eq(ACTOR), any())).thenReturn(true);
        when(repository.buscar(id)).thenReturn(Optional.of(anterior), Optional.of(actualizada));

        SolicitudActualizacionParametro solicitud = new SolicitudActualizacionParametro(
                90,
                new BigDecimal("1"),
                new BigDecimal("1440"),
                "NEGOCIO",
                null,
                LocalDate.of(2026, 10, 1),
                null);

        ParametroFila resultado = service.actualizar(id, solicitud, ACTOR, Set.of());

        assertThat(resultado.valor().asInt()).isEqualTo(90);
        verify(parametrosResueltos).invalidarTodo();
        verify(auditoria).registrar(eq(ACTOR), isNull(), eq("ACTUALIZAR"), eq("parametro"),
                eq(id), eq(anterior), eq(actualizada));
    }

    @Test
    void deniegaParametroDePaisFueraDelAlcanceDelUsuario() {
        UUID id = UUID.randomUUID();
        ParametroFila deColombia = new ParametroFila(
                id, "impuesto.general",
                objectMapper.valueToTree(new BigDecimal("0.19")),
                "DECIMAL", null, null,
                objectMapper.valueToTree(new BigDecimal("0.19")),
                "PAIS", UUID.randomUUID(), "US",
                LocalDate.of(2026, 1, 1), null, null, null);
        when(repository.buscar(id)).thenReturn(Optional.of(deColombia));
        Mockito.doThrow(new ErrorAplicacionException(HttpStatus.FORBIDDEN, "error.country.access.denied"))
                .when(accesoPais).validar(Set.of("CO"), "US");

        assertThatThrownBy(() -> service.obtener(id, Set.of("CO")))
                .isInstanceOf(ErrorAplicacionException.class);
    }

    @Test
    void deniegaCrearParametroParaPaisFueraDelAlcanceDelUsuario() {
        UUID estadosUnidos = UUID.randomUUID();
        when(repository.codigoPais(estadosUnidos)).thenReturn(Optional.of("US"));
        Mockito.doThrow(new ErrorAplicacionException(HttpStatus.FORBIDDEN, "error.country.access.denied"))
                .when(accesoPais).validar(Set.of("CO"), "US");

        SolicitudParametro solicitud = new SolicitudParametro(
                "cliente.umbral_frecuente",
                3,
                "ENTERO",
                null,
                null,
                "PAIS",
                estadosUnidos,
                LocalDate.of(2026, 1, 1),
                null);

        assertThatThrownBy(() -> service.crear(solicitud, ACTOR, Set.of("CO")))
                .isInstanceOfSatisfying(ErrorAplicacionException.class, error ->
                        assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository, never()).crear(any(), any(), any());
    }

    private ParametroFila fila(UUID id, int valor) {
        return new ParametroFila(
                id,
                "sesion.inactividad_minutos",
                objectMapper.valueToTree(valor),
                "ENTERO",
                new BigDecimal("1"),
                new BigDecimal("1440"),
                objectMapper.valueToTree(valor),
                "NEGOCIO",
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                ACTOR,
                java.time.OffsetDateTime.now());
    }
}
