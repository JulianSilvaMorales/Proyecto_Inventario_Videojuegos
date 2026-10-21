package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventario.domain.ParametroFila;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.SolicitudActualizacionParametro;
import com.inventario.dto.SolicitudParametro;
import com.inventario.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ParametroVentasPostgresIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ParametroAdminService parametros;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Transactional
    void creaParametroConVigenciaAbiertaRechazaSolapeYActualiza() {
        UUID actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.usuario ORDER BY creado_en_utc LIMIT 1",
                UUID.class);
        UUID colombia = jdbcTemplate.queryForObject(
                "SELECT id FROM public.pais WHERE codigo_iso = 'CO'",
                UUID.class);
        String clave = "integracion.prueba_" + UUID.randomUUID().toString().substring(0, 8);

        ParametroFila creado = parametros.crear(
                new SolicitudParametro(
                        clave, 3, "ENTERO", new BigDecimal("1"), new BigDecimal("10"),
                        "PAIS", colombia, LocalDate.of(2026, 1, 1), null),
                actorId,
                Set.of("CO"));

        assertThat(creado.paisCodigo()).isEqualTo("CO");
        assertThat(creado.valor().asInt()).isEqualTo(3);
        assertThatThrownBy(() -> parametros.crear(
                        new SolicitudParametro(
                                clave, 4, "ENTERO", null, null,
                                "PAIS", colombia, LocalDate.of(2027, 1, 1), null),
                        actorId,
                        Set.of("CO")))
                .isInstanceOfSatisfying(ErrorAplicacionException.class, error ->
                        assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));

        ParametroFila actualizado = parametros.actualizar(
                creado.id(),
                new SolicitudActualizacionParametro(
                        7, new BigDecimal("1"), new BigDecimal("10"),
                        "PAIS", colombia, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                actorId,
                Set.of("CO"));

        assertThat(actualizado.valor().asInt()).isEqualTo(7);
        assertThat(actualizado.vigenteHasta()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(parametros.listar(clave, "PAIS", Set.of("CO"), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
        assertThat(parametros.listar(clave, null, Set.of("US"), PageRequest.of(0, 10))
                        .getTotalElements())
                .isZero();
    }

    @Test
    @Transactional
    void apiAceptaDecimalesComoTextoEnValorYLimites() throws Exception {
        UUID actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.usuario ORDER BY creado_en_utc LIMIT 1",
                UUID.class);
        String clave = "integracion.decimal_" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/parametros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clave": "%s", "valor": "0.1234567890123456789", "tipo": "DECIMAL",
                                 "minimo": "0", "maximo": "1.0000", "ambito": "NEGOCIO",
                                 "vigenteDesde": "2026-01-01"}
                                """.formatted(clave))
                        .with(authentication(actor(actorId, "ADMINISTRADOR", "PARAMETROS_GESTIONAR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DECIMAL"));

        BigDecimal guardado = jdbcTemplate.queryForObject(
                "SELECT (valor #>> '{}')::numeric FROM public.parametro WHERE clave = ?",
                BigDecimal.class,
                clave);
        assertThat(guardado).isEqualByComparingTo("0.1234567890123456789");
    }

    @Test
    void listadoDeVentasExigePermisoYValidaEstado() throws Exception {
        mockMvc.perform(get("/api/v1/ventas")
                        .with(authentication(actor("VENDEDOR_CAJERO", "VENTAS_GESTIONAR"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ventas").param("estado", "OTRO")
                        .with(authentication(actor("VENDEDOR_CAJERO", "VENTAS_GESTIONAR"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/ventas")
                        .with(authentication(actor("EXPERTO_TECNICO", "INVENTARIO_CONSULTAR"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/parametros")
                        .with(authentication(actor("VENDEDOR_CAJERO", "VENTAS_GESTIONAR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void paisesDeVentaUsanMonedaLocalYMediosDePagoDelParametroDePais() throws Exception {
        mockMvc.perform(get("/api/v1/ventas/paises")
                        .with(authentication(actor("VENDEDOR_CAJERO", "VENTAS_GESTIONAR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paisCodigo").value("CO"))
                .andExpect(jsonPath("$[0].monedaCodigo").value("COP"))
                .andExpect(jsonPath("$[0].mediosPago").value(hasItem("NEQUI")));
        mockMvc.perform(get("/api/v1/referencias/producto")
                        .with(authentication(actor("VENDEDOR_CAJERO", "CATALOGO_CONSULTAR"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/referencias/usuario")
                        .with(authentication(actor("VENDEDOR_CAJERO", "VENTAS_GESTIONAR"))))
                .andExpect(status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken actor(String rol, String permiso) {
        return actor(UUID.randomUUID(), rol, permiso);
    }

    private UsernamePasswordAuthenticationToken actor(UUID usuarioId, String rol, String permiso) {
        UsuarioAutenticado actor = new UsuarioAutenticado(
                usuarioId,
                "integracion@local",
                "Usuario de integración",
                "es",
                List.of(rol),
                Set.of(permiso),
                Set.of("CO"),
                UUID.randomUUID());
        return UsernamePasswordAuthenticationToken.authenticated(
                actor,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + rol),
                        new SimpleGrantedAuthority(permiso)));
    }
}
