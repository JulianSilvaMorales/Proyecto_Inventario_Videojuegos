package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.inventario.domain.Cliente;
import com.inventario.dto.SolicitudCliente;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION", matches = "true")
class ClientePostgresIntegrationTest {

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Transactional
    void creaConsultaActualizaYDesactivaClienteConAlcancePorPais() {
        UUID paisId = jdbc.queryForObject(
                "SELECT id FROM public.pais WHERE codigo_iso = 'CO'",
                UUID.class);
        UUID actorId = jdbc.queryForObject(
                "SELECT id FROM public.usuario LIMIT 1",
                UUID.class);

        Cliente created = clienteService.crear(
                new SolicitudCliente(
                        paisId,
                        "FRECUENTE",
                        "Cliente integración",
                        "Prueba",
                        null,
                        null,
                        "110111",
                        "Calle de prueba 1",
                        "3001234567",
                        "cliente-integration@example.com",
                        "Consentimiento informado",
                        true,
                        true),
                actorId,
                Set.of("CO"));

        assertThat(clienteService.listar(
                        "cliente-integration@example.com",
                        Set.of("CO"),
                        PageRequest.of(0, 10))
                .getContent()).extracting(Cliente::id).containsExactly(created.id());
        assertThat(clienteService.listar(
                        null,
                        Set.of("US"),
                        PageRequest.of(0, 10))
                .getContent()).doesNotContain(created);

        Cliente updated = clienteService.actualizar(
                created.id(),
                new SolicitudCliente(
                        paisId,
                        "FRECUENTE",
                        "Cliente integración",
                        "Actualizado",
                        null,
                        null,
                        "110111",
                        "Calle de prueba 2",
                        "3001234567",
                        "cliente-integration@example.com",
                        "Consentimiento informado",
                        true,
                        true),
                actorId,
                Set.of("CO"));
        assertThat(updated.apellidos()).isEqualTo("Actualizado");

        clienteService.desactivar(created.id(), actorId, Set.of("CO"));

        assertThat(clienteService.obtener(created.id(), Set.of("CO")).activo()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.cliente WHERE id = ?",
                Integer.class,
                created.id())).isEqualTo(1);
    }
}
