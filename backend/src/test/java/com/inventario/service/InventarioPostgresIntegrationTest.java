package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventario.dto.SolicitudAjusteInventario;
import com.inventario.dto.SolicitudBodega;
import com.inventario.dto.SolicitudEntradaInventario;
import com.inventario.dto.SolicitudLineaEntradaInventario;
import com.inventario.dto.SolicitudLineaOrdenCompra;
import com.inventario.dto.SolicitudProveedor;
import com.inventario.dto.SolicitudSalidaInventario;
import com.inventario.dto.SolicitudOrdenCompra;
import com.inventario.domain.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION", matches = "true")
class InventarioPostgresIntegrationTest {

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private ComprasService comprasService;

    @Autowired
    private AjustesService ajustesService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protegeConsultasYSalidasConPermisosSeparados() throws Exception {
        mockMvc.perform(get("/api/v1/inventario/saldos")
                        .with(user("consulta").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "INVENTARIO_CONSULTAR"))))
                .andExpect(status().isOk());

        String solicitud = """
                {
                  "productoId": "%s",
                  "bodegaId": "%s",
                  "tipo": "PERDIDA",
                  "cantidad": 1,
                  "proveedorId": "%s",
                  "numeroReporteProveedor": "REP-TEST"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/inventario/salidas")
                        .with(user("consulta").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "INVENTARIO_CONSULTAR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inventario/salidas")
                        .with(authentication(authenticationActor(
                                UUID.randomUUID(),
                                "ADMINISTRADOR",
                                "SALIDAS_INVENTARIO_GESTIONAR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud))
                .andExpect(status().isNotFound());

        String ajuste = """
                {
                  "productoId": "%s",
                  "bodegaId": "%s",
                  "cantidadContada": 0,
                  "motivo": "Prueba de permisos"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/inventario/ajustes/operativos")
                        .with(authentication(authenticationActor(
                                UUID.randomUUID(),
                                "ADMINISTRADOR",
                                "AJUSTE_OPERATIVO_SOLICITAR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ajuste))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inventario/ajustes/tecnicos")
                        .with(authentication(authenticationActor(
                                UUID.randomUUID(),
                                "ADMINISTRADOR",
                                "AJUSTE_TECNICO_SOLICITAR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ajuste))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inventario/ajustes/operativos")
                        .with(authentication(authenticationActor(
                                UUID.randomUUID(),
                                "ENCARGADO_INVENTARIO",
                                "AJUSTE_OPERATIVO_SOLICITAR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ajuste))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/inventario/ajustes/tecnicos")
                        .with(authentication(authenticationActor(
                                UUID.randomUUID(),
                                "EXPERTO_TECNICO",
                                "AJUSTE_TECNICO_SOLICITAR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ajuste))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void recibeParcialmenteRegistraSalidaYAplicaAjusteConAuditoria() {
        UUID actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.usuario ORDER BY creado_en_utc LIMIT 1",
                UUID.class);
        UUID countryId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.pais WHERE codigo_iso = 'CO'",
                UUID.class);
        UUID usdId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.moneda WHERE codigo_iso = 'USD'",
                UUID.class);
        UUID typeId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID subcategoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        jdbcTemplate.update(
                "INSERT INTO public.tipo_producto (id, codigo, nombre, activo)"
                        + " VALUES (?, ?, ?, TRUE)",
                typeId,
                "E54-T-" + suffix,
                "Tipo temporal de integración");
        jdbcTemplate.update(
                "INSERT INTO public.categoria (id, codigo, nombre, padre_id, activo)"
                        + " VALUES (?, ?, ?, NULL, TRUE)",
                categoryId,
                "E54-C-" + suffix,
                "Categoría temporal");
        jdbcTemplate.update(
                "INSERT INTO public.categoria (id, codigo, nombre, padre_id, activo)"
                        + " VALUES (?, ?, ?, ?, TRUE)",
                subcategoryId,
                "E54-S-" + suffix,
                "Subcategoría temporal",
                categoryId);
        jdbcTemplate.update(
                """
                INSERT INTO public.producto
                    (id, codigo_interno, tipo_producto_id, categoria_id, subcategoria_id,
                     atributos, activo, creado_en_utc, actualizado_en_utc)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), TRUE, ?, ?)
                """,
                productId,
                "E54-PRD-" + suffix,
                typeId,
                categoryId,
                subcategoryId,
                "{}",
                now,
                now);

        var bodega = inventarioService.crearBodega(
                new SolicitudBodega("E54-B-" + suffix, "Bodega integración", null, true),
                actorId);
        assertThat(inventarioService.listarBodegas(
                        "E54-B-" + suffix, PageRequest.of(0, 10)).getContent())
                .extracting("id")
                .containsExactly(bodega.id());
        var proveedor = inventarioService.crearProveedor(
                new SolicitudProveedor(
                        "Proveedor integración " + suffix,
                        countryId,
                        "E54-" + suffix,
                        "Contacto",
                        "inventario-" + suffix + "@example.invalid",
                        null,
                        usdId,
                        "Pago contra entrega",
                        true),
                actorId);
        assertThat(inventarioService.listarProveedores(
                        suffix, PageRequest.of(0, 10)).getContent())
                .extracting("id")
                .containsExactly(proveedor.id());
        inventarioService.vincularProductoProveedor(
                proveedor.id(), productId, actorId);

        var orden = comprasService.crearOrden(
                new SolicitudOrdenCompra(
                        proveedor.id(),
                        new BigDecimal("4000.00000000"),
                        List.of(new SolicitudLineaOrdenCompra(
                                productId, 5, new BigDecimal("10.0000")))),
                actorId);
        assertThat(comprasService.listarOrdenes(
                        proveedor.id(), "BORRADOR", PageRequest.of(0, 10)).getContent())
                .extracting("id")
                .containsExactly(orden.orden().id());
        var linea = orden.lineas().get(0);
        comprasService.enviarOrden(orden.orden().id(), actorId);
        var primeraEntrada = comprasService.recibir(
                new SolicitudEntradaInventario(
                        orden.orden().id(),
                        bodega.id(),
                        "E54-FAC-A-" + suffix,
                        new BigDecimal("4100.00000000"),
                        List.of(new SolicitudLineaEntradaInventario(linea.id(), 2))),
                actorId);
        assertThat(primeraEntrada.lineas().get(0).costoUnitarioMonedaBase())
                .isEqualByComparingTo("41000.0000");
        assertThat(inventarioService.listarSaldos(
                        productId, bodega.id(), false, PageRequest.of(0, 10))
                        .getContent().get(0).cantidad())
                .isEqualTo(2);
        comprasService.recibir(
                new SolicitudEntradaInventario(
                        orden.orden().id(),
                        bodega.id(),
                        "E54-FAC-B-" + suffix,
                        new BigDecimal("4100.00000000"),
                        List.of(new SolicitudLineaEntradaInventario(linea.id(), 3))),
                actorId);

        inventarioService.registrarSalida(
                new SolicitudSalidaInventario(
                        productId,
                        bodega.id(),
                        "PERDIDA",
                        1,
                        proveedor.id(),
                        "REP-" + suffix,
                        "Prueba de salida"),
                actorId);
        var ajuste = ajustesService.solicitar(
                "OPERATIVO",
                new SolicitudAjusteInventario(
                        productId, bodega.id(), 3, "Conteo físico de integración"),
                actorId);
        ajustesService.aprobar(ajuste.id(), actorId);
        var aplicado = ajustesService.aplicar(ajuste.id(), actorId);
        assertThat(ajustesService.listar(
                        "APLICADO", "OPERATIVO", productId, bodega.id(), PageRequest.of(0, 10))
                        .getContent())
                .extracting("id")
                .containsExactly(ajuste.id());
        assertThat(inventarioService.listarMovimientos(
                        productId,
                        bodega.id(),
                        actorId,
                        null,
                        null,
                        null,
                        PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(4);

        Integer balance = jdbcTemplate.queryForObject(
                """
                SELECT cantidad FROM public.saldo_inventario
                WHERE producto_id = ? AND bodega_id = ?
                """,
                Integer.class,
                productId,
                bodega.id());
        Integer movimientos = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM public.movimiento_inventario
                WHERE producto_id = ? AND bodega_id = ?
                """,
                Integer.class,
                productId,
                bodega.id());
        Integer auditorias = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM public.auditoria
                WHERE registro_id IN (?, ?, ?, ?)
                """,
                Integer.class,
                bodega.id(),
                proveedor.id(),
                orden.orden().id(),
                ajuste.id());

        assertThat(aplicado.estado()).isEqualTo("APLICADO");
        assertThat(balance).isEqualTo(3);
        assertThat(movimientos).isEqualTo(4);
        assertThat(auditorias).isGreaterThanOrEqualTo(6);
    }

    private UsernamePasswordAuthenticationToken authenticationActor(
            UUID actorId,
            String rol,
            String permiso) {
        UsuarioAutenticado actor = new UsuarioAutenticado(
                actorId,
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
