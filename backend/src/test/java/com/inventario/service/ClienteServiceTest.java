package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inventario.domain.Cliente;
import com.inventario.domain.Pais;
import com.inventario.dto.SolicitudCliente;
import com.inventario.repository.ClienteRepository;
import com.inventario.repository.ConfiguracionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    private static final UUID PAIS_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final OffsetDateTime AHORA =
            OffsetDateTime.parse("2026-10-20T12:00:00Z");

    @Mock
    private ClienteRepository repository;

    @Mock
    private ConfiguracionRepository configuracion;

    @Mock
    private AuditoriaService auditoria;

    @Test
    void creaClienteEnPaisPermitidoYAuditaSinDatosPersonales() {
        Pais pais = new Pais(PAIS_ID, "CO", "Colombia", null, null, "America/Bogota", true);
        Cliente cliente = new Cliente(
                CLIENTE_ID,
                PAIS_ID,
                "FRECUENTE",
                "Ana",
                "Pérez",
                null,
                null,
                "110111",
                "Calle 1",
                "3001234567",
                "ana@example.com",
                "Consentimiento",
                true,
                AHORA,
                AHORA,
                true);
        when(configuracion.buscarPais(PAIS_ID)).thenReturn(Optional.of(pais));
        when(repository.crear(any(Cliente.class))).thenReturn(CLIENTE_ID);
        when(repository.buscar(CLIENTE_ID)).thenReturn(Optional.of(cliente));
        ClienteService service = service();

        Cliente created = service.crear(solicitud(), ACTOR_ID, Set.of("CO"));

        assertThat(created).isEqualTo(cliente);
        ArgumentCaptor<Object> auditData = ArgumentCaptor.forClass(Object.class);
        verify(auditoria).registrar(
                eq(ACTOR_ID),
                eq(PAIS_ID),
                eq("CREAR"),
                eq("cliente"),
                eq(CLIENTE_ID),
                isNull(),
                auditData.capture());
        assertThat(auditData.getValue().toString())
                .contains("FRECUENTE")
                .doesNotContain("Ana", "Pérez", "ana@example.com");
    }

    @Test
    void rechazaCrearClienteFueraDelAlcanceDePais() {
        Pais pais = new Pais(PAIS_ID, "CO", "Colombia", null, null, "America/Bogota", true);
        when(configuracion.buscarPais(PAIS_ID)).thenReturn(Optional.of(pais));
        ClienteService service = service();

        assertThatThrownBy(() -> service.crear(solicitud(), ACTOR_ID, Set.of("US")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(repository, never()).crear(any(Cliente.class));
    }

    @Test
    void noPermiteCambiarElPaisDeUnClienteExistente() {
        UUID otroPaisId = UUID.randomUUID();
        Pais pais = new Pais(PAIS_ID, "CO", "Colombia", null, null, "America/Bogota", true);
        Cliente existing = new Cliente(
                CLIENTE_ID,
                PAIS_ID,
                "FRECUENTE",
                "Ana",
                "Pérez",
                null,
                null,
                "110111",
                "Calle 1",
                "3001234567",
                "ana@example.com",
                "Consentimiento",
                true,
                AHORA,
                AHORA,
                true);
        when(repository.buscar(CLIENTE_ID)).thenReturn(Optional.of(existing));
        when(configuracion.buscarPais(PAIS_ID)).thenReturn(Optional.of(pais));
        ClienteService service = service();

        assertThatThrownBy(() -> service.actualizar(
                        CLIENTE_ID,
                        new SolicitudCliente(
                                otroPaisId,
                                "FRECUENTE",
                                "Ana",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "ana@example.com",
                                "Consentimiento",
                                true,
                                true),
                        ACTOR_ID,
                        Set.of("CO")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));

        verify(repository, never()).actualizar(any(Cliente.class));
    }

    @Test
    void permiteRegistrarRevocacionDeConsentimientoEnActualizacion() {
        OffsetDateTime consentimientoPrevio = AHORA.minusHours(1);
        Pais pais = new Pais(PAIS_ID, "CO", "Colombia", null, null, "America/Bogota", true);
        Cliente existing = new Cliente(
                CLIENTE_ID,
                PAIS_ID,
                "FRECUENTE",
                "Ana",
                "Pérez",
                null,
                null,
                "110111",
                "Calle 1",
                "3001234567",
                "ana@example.com",
                "Consentimiento",
                true,
                consentimientoPrevio,
                consentimientoPrevio,
                true);
        Cliente updated = new Cliente(
                CLIENTE_ID,
                PAIS_ID,
                "FRECUENTE",
                "Ana",
                "Pérez",
                null,
                null,
                "110111",
                "Calle 1",
                "3001234567",
                "ana@example.com",
                "Consentimiento",
                false,
                AHORA,
                consentimientoPrevio,
                true);
        when(repository.buscar(CLIENTE_ID))
                .thenReturn(Optional.of(existing), Optional.of(updated));
        when(repository.actualizar(any(Cliente.class))).thenReturn(true);
        when(configuracion.buscarPais(PAIS_ID)).thenReturn(Optional.of(pais));
        ClienteService service = service();

        Cliente result = service.actualizar(
                CLIENTE_ID,
                new SolicitudCliente(
                        PAIS_ID,
                        "FRECUENTE",
                        "Ana",
                        "Pérez",
                        null,
                        null,
                        "110111",
                        "Calle 1",
                        "3001234567",
                        "ana@example.com",
                        "Consentimiento",
                        false,
                        true),
                ACTOR_ID,
                Set.of("CO"));

        assertThat(result.consentimientoRegistrado()).isFalse();
        assertThat(result.consentimientoEnUtc()).isEqualTo(AHORA);
    }

    private ClienteService service() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);
        return new ClienteService(
                repository,
                configuracion,
                new AccesoPaisService(),
                auditoria,
                clock);
    }

    private static SolicitudCliente solicitud() {
        return new SolicitudCliente(
                PAIS_ID,
                "FRECUENTE",
                " Ana ",
                " Pérez ",
                null,
                null,
                "110111",
                " Calle 1 ",
                "3001234567",
                " ANA@EXAMPLE.COM ",
                " Consentimiento ",
                true,
                true);
    }
}
