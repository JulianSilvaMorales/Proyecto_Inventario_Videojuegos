package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.SolicitudActualizacionProducto;
import com.inventario.dto.SolicitudCategoria;
import com.inventario.dto.SolicitudDefinicionAtributo;
import com.inventario.dto.SolicitudPrecioProducto;
import com.inventario.dto.SolicitudProducto;
import com.inventario.dto.SolicitudProductoTraduccion;
import com.inventario.dto.SolicitudTipoProducto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION", matches = "true")
class CatalogoPostgresIntegrationTest {

    @Autowired
    private CatalogoService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Transactional
    void apiPermiteConsultarYLimitaEscriturasAlPermisoDeGestion() throws Exception {
        UUID actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.usuario ORDER BY creado_en_utc LIMIT 1",
                UUID.class);
        String body = """
                {
                  "codigo": "E53-API",
                  "nombre": "Categoría API temporal",
                  "padreId": null,
                  "activo": true
                }
                """;

        mockMvc.perform(get("/api/v1/catalogo/categorias")
                        .with(user("consulta").authorities(
                                new SimpleGrantedAuthority("CATALOGO_CONSULTAR"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/catalogo/categorias")
                        .with(user("consulta").authorities(
                                new SimpleGrantedAuthority("CATALOGO_CONSULTAR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/catalogo/categorias")
                        .with(authentication(authenticationActor(actorId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("E53-API"));
    }

    @Test
    @Transactional
    void flujoDeCatalogoGuardaReglasYSeRevierteAlFinalizar() throws Exception {
        UUID actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.usuario ORDER BY creado_en_utc LIMIT 1",
                UUID.class);
        UUID idiomaId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.idioma WHERE codigo_bcp47 = 'es' AND habilitado",
                UUID.class);
        UUID monedaId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.moneda WHERE codigo_iso = 'COP'",
                UUID.class);
        LocalDate starts = LocalDate.now(clock);

        var category = service.crearCategoria(
                new SolicitudCategoria(
                        "E53-" + UUID.randomUUID().toString().substring(0, 8),
                        "Integración",
                        null,
                        true),
                actorId);
        var subcategory = service.crearCategoria(
                new SolicitudCategoria(
                        "E53-" + UUID.randomUUID().toString().substring(0, 8),
                        "Subcategoría integración",
                        category.id(),
                        true),
                actorId);
        var type = service.crearTipoProducto(
                new SolicitudTipoProducto(
                        "E53-" + UUID.randomUUID().toString().substring(0, 8),
                        "Tipo integración",
                        true),
                actorId);
        service.crearDefinicion(
                new SolicitudDefinicionAtributo(
                        type.id(),
                        "plataforma",
                        "TEXTO",
                        true,
                        objectMapper.readTree("[\"E53-PLATAFORMA\"]")),
                actorId);

        var product = service.crearProducto(
                new SolicitudProducto(
                        type.id(),
                        category.id(),
                        subcategory.id(),
                        objectMapper.readTree("{\"plataforma\":\"E53-PLATAFORMA\"}"),
                        List.of(new SolicitudProductoTraduccion(
                                idiomaId,
                                "Producto integración",
                                "Registro temporal de prueba")),
                        List.of(new SolicitudPrecioProducto(
                                monedaId,
                                new BigDecimal("12.5000"),
                                starts,
                                null))),
                actorId);

        assertThat(product.producto().codigoInterno()).startsWith("PRD-");
        assertThat(product.producto().atributos().get("plataforma").asText())
                .isEqualTo("E53-PLATAFORMA");
        assertThat(product.traducciones()).hasSize(1);
        assertThat(product.precios()).singleElement()
                .satisfies(price -> assertThat(price.importe()).isEqualByComparingTo("12.5000"));
        mockMvc.perform(get("/api/v1/catalogo/productos/" + product.producto().id())
                        .with(user("consulta").authorities(
                                new SimpleGrantedAuthority("CATALOGO_CONSULTAR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.producto.codigoInterno")
                        .value(product.producto().codigoInterno()))
                .andExpect(jsonPath("$.traducciones[0].idiomaCodigo").value("es"))
                .andExpect(jsonPath("$.precios[0].monedaCodigo").value("COP"));

        assertThatThrownBy(() -> service.agregarPrecio(
                product.producto().id(),
                new SolicitudPrecioProducto(
                        monedaId,
                        new BigDecimal("13.0000"),
                        starts,
                        null),
                actorId))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.messageKey())
                                .isEqualTo("error.catalog.price.overlap"));

        var updated = service.actualizarProducto(
                product.producto().id(),
                new SolicitudActualizacionProducto(
                        type.id(),
                        category.id(),
                        subcategory.id(),
                        objectMapper.readTree("{\"plataforma\":\"E53-PLATAFORMA\"}")),
                actorId);
        assertThat(updated.producto().actualizadoEnUtc())
                .isAfterOrEqualTo(product.producto().actualizadoEnUtc());

        var inactive = service.desactivarProducto(product.producto().id(), actorId);
        assertThat(inactive.producto().activo()).isFalse();
        service.desactivarTipoProducto(type.id(), actorId);
        service.desactivarCategoria(subcategory.id(), actorId);
        service.desactivarCategoria(category.id(), actorId);
    }

    private UsernamePasswordAuthenticationToken authenticationActor(UUID actorId) {
        UsuarioAutenticado actor = new UsuarioAutenticado(
                actorId,
                "integracion@local",
                "Usuario de integración",
                "es",
                List.of("ADMINISTRADOR"),
                Set.of("CATALOGO_CONSULTAR", "PRODUCTOS_GESTIONAR"),
                Set.of("CO"),
                UUID.randomUUID());
        return UsernamePasswordAuthenticationToken.authenticated(
                actor,
                null,
                List.of(
                        new SimpleGrantedAuthority("CATALOGO_CONSULTAR"),
                        new SimpleGrantedAuthority("PRODUCTOS_GESTIONAR")));
    }
}
