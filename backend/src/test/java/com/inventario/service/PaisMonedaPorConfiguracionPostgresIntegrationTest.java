package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * E7: un país y una moneda nuevos se agregan solo con datos, usando la API de
 * administración, y la venta funciona con ellos sin cambiar código. Todo se
 * ejecuta en una transacción que se revierte al terminar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PaisMonedaPorConfiguracionPostgresIntegrationTest extends PostgresIntegrationTest {

    private static final List<String> PERMISOS = List.of(
            "PARAMETROS_GESTIONAR",
            "TIPOS_CAMBIO_GESTIONAR",
            "PRODUCTOS_GESTIONAR",
            "CATALOGO_CONSULTAR",
            "COMPRAS_GESTIONAR",
            "INVENTARIO_CONSULTAR",
            "VENTAS_GESTIONAR");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UsernamePasswordAuthenticationToken administrador;

    @Test
    @Transactional
    void ventaEnPaisYMonedaAgregadosSoloConDatos() throws Exception {
        UUID actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.usuario ORDER BY creado_en_utc LIMIT 1", UUID.class);
        administrador = actor(actorId);
        UUID idiomaId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.idioma WHERE codigo_bcp47 = 'es'", UUID.class);
        UUID monedaBaseId = jdbcTemplate.queryForObject(
                """
                SELECT m.id FROM public.moneda m
                JOIN public.parametro p ON p.clave = 'moneda.base_codigo_iso'
                    AND p.ambito = 'NEGOCIO' AND m.codigo_iso = p.valor #>> '{}'
                """,
                UUID.class);
        String codigoMoneda = codigoLibre("moneda", "codigo_iso", 3);
        String codigoPais = codigoLibre("pais", "codigo_iso", 2);
        LocalDate ayer = LocalDate.now().minusDays(1);

        // 1. Moneda, país (inhabilitado), impuesto, habilitación y tipo de cambio.
        UUID monedaId = id(enviar(post("/api/v1/configuracion/monedas"), """
                {"codigoIso": "%s", "nombre": "Moneda de prueba", "simbolo": "¤",
                 "decimales": 2, "formato": "es-MX", "reglaRedondeo": "HALF_UP"}
                """.formatted(codigoMoneda), 201));
        String paisJson = """
                {"codigoIso": "%s", "nombre": "País de prueba", "monedaLocalId": "%s",
                 "idiomaPredeterminadoId": "%s", "zonaHoraria": "America/Mexico_City",
                 "habilitado": %s}
                """;
        UUID paisId = id(enviar(post("/api/v1/configuracion/paises"),
                paisJson.formatted(codigoPais, monedaId, idiomaId, false), 201));
        enviar(post("/api/v1/configuracion/impuestos"), """
                {"paisId": "%s", "nombre": "Impuesto de prueba", "tasa": "0.16",
                 "incluidoEnPrecio": false, "vigenteDesde": "%s"}
                """.formatted(paisId, ayer), 201);
        enviar(put("/api/v1/configuracion/paises/" + paisId),
                paisJson.formatted(codigoPais, monedaId, idiomaId, true), 200);
        enviar(post("/api/v1/configuracion/tipos-cambio"), """
                {"monedaOrigenId": "%s", "monedaDestinoId": "%s", "tasa": "200.50000000",
                 "vigenteDesdeUtc": "%s"}
                """.formatted(monedaId, monedaBaseId, Instant.now().minus(1, ChronoUnit.HOURS)), 201);

        // 2. Producto con precio en la moneda nueva y bodega en el país nuevo.
        UUID tipoId = UUID.randomUUID();
        UUID categoriaId = UUID.randomUUID();
        UUID subcategoriaId = UUID.randomUUID();
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update(
                "INSERT INTO public.tipo_producto (id, codigo, nombre, activo) VALUES (?, ?, ?, TRUE)",
                tipoId, "E7-T-" + sufijo, "Tipo E7");
        jdbcTemplate.update(
                "INSERT INTO public.categoria (id, codigo, nombre, padre_id, activo) VALUES (?, ?, ?, NULL, TRUE)",
                categoriaId, "E7-C-" + sufijo, "Categoría E7");
        jdbcTemplate.update(
                "INSERT INTO public.categoria (id, codigo, nombre, padre_id, activo) VALUES (?, ?, ?, ?, TRUE)",
                subcategoriaId, "E7-S-" + sufijo, "Subcategoría E7", categoriaId);
        JsonNode producto = enviar(post("/api/v1/catalogo/productos"), """
                {"tipoProductoId": "%s", "categoriaId": "%s", "subcategoriaId": "%s",
                 "atributos": {},
                 "traducciones": [{"idiomaId": "%s", "nombre": "Producto E7"}],
                 "precios": [{"monedaId": "%s", "importe": "100.00", "vigenteDesde": "%s"}]}
                """.formatted(tipoId, categoriaId, subcategoriaId, idiomaId, monedaId, ayer), 201);
        UUID productoId = UUID.fromString(producto.path("producto").path("id").asText());
        UUID bodegaId = id(enviar(post("/api/v1/inventario/bodegas"), """
                {"codigo": "E7-B-%s", "nombre": "Bodega E7", "paisId": "%s", "activo": true}
                """.formatted(sufijo, paisId), 201));
        jdbcTemplate.update(
                """
                INSERT INTO public.saldo_inventario (id, producto_id, bodega_id, cantidad, actualizado_en_utc)
                VALUES (?, ?, ?, 10, ?)
                """,
                UUID.randomUUID(), productoId, bodegaId, OffsetDateTime.now());

        // 3. El país aparece para vender con su moneda local.
        JsonNode paises = enviar(get("/api/v1/ventas/paises"), null, 200);
        assertThat(paises.findValuesAsText("paisCodigo")).contains(codigoPais.toUpperCase());

        // 4. Venta pendiente: precio, impuesto y tipo de cambio del país y la moneda nuevos.
        JsonNode venta = enviar(post("/api/v1/ventas"), """
                {"paisId": "%s", "monedaId": "%s",
                 "lineas": [{"productoId": "%s", "bodegaId": "%s", "cantidad": 2}]}
                """.formatted(paisId, monedaId, productoId, bodegaId), 201).path("venta");
        assertThat(venta.path("monedaCodigo").asText()).isEqualTo(codigoMoneda.toUpperCase());
        assertThat(venta.path("subtotal").isTextual()).isTrue();
        assertThat(decimal(venta, "subtotal")).isEqualByComparingTo("200.00");
        assertThat(decimal(venta, "totalImpuestos")).isEqualByComparingTo("32.00");
        assertThat(decimal(venta, "total")).isEqualByComparingTo("232.00");
        assertThat(decimal(venta, "tipoCambioAplicado")).isEqualByComparingTo("200.5");
        assertThat(decimal(venta, "totalMonedaBase")).isEqualByComparingTo("46516.00");

        // 5. Confirmación: descuenta inventario y registra el movimiento de venta.
        String ventaId = venta.path("id").asText();
        JsonNode confirmada = enviar(post("/api/v1/ventas/" + ventaId + "/confirmar"),
                "{\"medioPago\": \"EFECTIVO\"}", 200).path("venta");
        assertThat(confirmada.path("estado").asText()).isEqualTo("CONFIRMADA");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT cantidad FROM public.saldo_inventario WHERE producto_id = ? AND bodega_id = ?",
                        Integer.class, productoId, bodegaId))
                .isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM public.movimiento_inventario
                        WHERE producto_id = ? AND bodega_id = ? AND tipo = 'SALIDA_VENTA'
                        """,
                        Integer.class, productoId, bodegaId))
                .isEqualTo(1);
    }

    private JsonNode enviar(MockHttpServletRequestBuilder request, String body, int esperado)
            throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String respuesta = mockMvc.perform(request.with(authentication(administrador)))
                .andExpect(status().is(esperado))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(respuesta);
    }

    private static UUID id(JsonNode nodo) {
        return UUID.fromString(nodo.path("id").asText());
    }

    private static BigDecimal decimal(JsonNode nodo, String campo) {
        return new BigDecimal(nodo.path(campo).asText());
    }

    /** Código ISO de prueba (letras) que todavía no existe en la tabla indicada. */
    private String codigoLibre(String tabla, String columna, int longitud) {
        while (true) {
            StringBuilder codigo = new StringBuilder("Q");
            for (int i = 1; i < longitud; i++) {
                codigo.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
            }
            Integer usados = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM public." + tabla + " WHERE upper(" + columna + ") = ?",
                    Integer.class,
                    codigo.toString());
            if (usados != null && usados == 0) {
                return codigo.toString();
            }
        }
    }

    private static UsernamePasswordAuthenticationToken actor(UUID usuarioId) {
        UsuarioAutenticado actor = new UsuarioAutenticado(
                usuarioId,
                "admin@local",
                "Administrador",
                "es",
                List.of("ADMINISTRADOR"),
                Set.copyOf(PERMISOS),
                Set.of(),
                UUID.randomUUID());
        List<SimpleGrantedAuthority> autoridades = new java.util.ArrayList<>(
                PERMISOS.stream().map(SimpleGrantedAuthority::new).toList());
        autoridades.add(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
        return UsernamePasswordAuthenticationToken.authenticated(actor, null, autoridades);
    }
}
