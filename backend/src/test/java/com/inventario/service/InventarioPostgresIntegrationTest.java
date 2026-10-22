package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.SolicitudAjusteInventario;
import com.inventario.dto.SolicitudBodega;
import com.inventario.dto.SolicitudEntradaInventario;
import com.inventario.dto.SolicitudLineaEntradaInventario;
import com.inventario.dto.SolicitudLineaOrdenCompra;
import com.inventario.dto.SolicitudLineaVenta;
import com.inventario.dto.SolicitudOrdenCompra;
import com.inventario.dto.SolicitudProveedor;
import com.inventario.dto.SolicitudSalidaInventario;
import com.inventario.dto.SolicitudVenta;
import com.inventario.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
class InventarioPostgresIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private ComprasService comprasService;

    @Autowired
    private AjustesService ajustesService;

    @Autowired
    private VentasService ventasService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protegeConsultasYSalidasConPermisosSeparados() throws Exception {
        mockMvc.perform(get("/api/v1/inventario/saldos")
                        .with(authentication(authenticationActor(
                                UUID.randomUUID(),
                                "ENCARGADO_INVENTARIO",
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

        mockMvc.perform(get("/api/v1/reportes/inventario/existencias")
                        .with(user("consulta").authorities(
                                new SimpleGrantedAuthority("INVENTARIO_CONSULTAR"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reportes/inventario/existencias")
                        .with(authentication(authenticationActor(
                                UUID.randomUUID(),
                                "ENCARGADO_INVENTARIO",
                                "REPORTES_INVENTARIO"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ventas/" + UUID.randomUUID())
                        .with(user("consulta").authorities(
                                new SimpleGrantedAuthority("INVENTARIO_CONSULTAR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void recibeRegistraVentaYAplicaAjusteConAuditoria() throws Exception {
        UUID actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.usuario ORDER BY creado_en_utc LIMIT 1",
                UUID.class);
        UUID countryId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.pais WHERE codigo_iso = 'CO'",
                UUID.class);
        UUID usCountryId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.pais WHERE codigo_iso = 'US'",
                UUID.class);
        jdbcTemplate.update(
                "UPDATE public.pais SET habilitado = TRUE WHERE id = ?",
                usCountryId);
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
                new SolicitudBodega(
                        "E54-B-" + suffix,
                        "Bodega integración",
                        null,
                        countryId,
                        true),
                actorId,
                Set.of("CO"));
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
                actorId,
                Set.of("CO"));
        assertThat(inventarioService.listarProveedores(
                        suffix, Set.of("CO"), PageRequest.of(0, 10)).getContent())
                .extracting("id")
                .containsExactly(proveedor.id());
        assertThat(inventarioService.listarProveedores(
                        suffix, Set.of("US"), PageRequest.of(0, 10)).getContent())
                .isEmpty();
        assertThatThrownBy(() -> inventarioService.obtenerProveedor(
                        proveedor.id(), Set.of("US")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.status())
                                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        mockMvc.perform(get("/api/v1/proveedores/{id}", proveedor.id())
                        .with(authentication(authenticationActor(
                                actorId,
                                "ENCARGADO_INVENTARIO",
                                "PROVEEDORES_CONSULTAR",
                                Set.of("US")))))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> inventarioService.crearProveedor(
                        new SolicitudProveedor(
                                "Proveedor fuera de alcance " + suffix,
                                countryId,
                                null,
                                null,
                                null,
                                null,
                                usdId,
                                null,
                                true),
                        actorId,
                        Set.of("US")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.status())
                                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> inventarioService.actualizarProveedor(
                        proveedor.id(),
                        new SolicitudProveedor(
                                "Proveedor integración " + suffix,
                                usCountryId,
                                "E54-" + suffix,
                                "Contacto",
                                "inventario-" + suffix + "@example.invalid",
                                null,
                                usdId,
                                "Pago contra entrega",
                                true),
                        actorId,
                        Set.of("CO", "US")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> {
                            assertThat(exception.status())
                                    .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                            assertThat(exception.messageKey())
                                    .isEqualTo("error.inventory.supplier.country.immutable");
                        });
        inventarioService.vincularProductoProveedor(
                proveedor.id(), productId, actorId, Set.of("CO"));
        assertThatThrownBy(() -> inventarioService.vincularProductoProveedor(
                        proveedor.id(), productId, actorId, Set.of("US")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.status())
                                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThat(inventarioService.listarProductosProveedor(
                        proveedor.id(), "es", Set.of("CO")))
                .extracting("id")
                .containsExactly(productId);
        assertThatThrownBy(() -> inventarioService.listarProductosProveedor(
                        proveedor.id(), "es", Set.of("US")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.status())
                                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        mockMvc.perform(get("/api/v1/proveedores/{id}/productos", proveedor.id())
                        .with(authentication(authenticationActor(
                                actorId,
                                "ENCARGADO_INVENTARIO",
                                "PROVEEDORES_CONSULTAR",
                                Set.of("CO")))))
                .andExpect(status().isOk());
        assertThat(comprasService.contexto().monedaBaseCodigo()).isEqualTo("COP");

        var orden = comprasService.crearOrden(
                new SolicitudOrdenCompra(
                        proveedor.id(),
                        new BigDecimal("4000.00000000"),
                        List.of(new SolicitudLineaOrdenCompra(
                                productId, 5, new BigDecimal("10.0000")))),
                actorId,
                Set.of("CO"));
        assertThat(comprasService.listarOrdenes(
                        proveedor.id(), "BORRADOR", Set.of("CO"), PageRequest.of(0, 10))
                        .getContent())
                .extracting("id")
                .containsExactly(orden.orden().id());
        assertThat(comprasService.listarOrdenes(
                        null, null, Set.of("US"), PageRequest.of(0, 10)).getContent())
                .doesNotContain(orden.orden());
        assertThatThrownBy(() -> comprasService.obtenerOrden(
                        orden.orden().id(), Set.of("US")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.status())
                                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        mockMvc.perform(get(
                                "/api/v1/compras/ordenes/{id}",
                                orden.orden().id())
                        .with(authentication(authenticationActor(
                                actorId,
                                "ENCARGADO_INVENTARIO",
                                "COMPRAS_GESTIONAR",
                                Set.of("US")))))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> comprasService.enviarOrden(
                        orden.orden().id(), actorId, Set.of("US")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.status())
                                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> comprasService.crearOrden(
                        new SolicitudOrdenCompra(
                                proveedor.id(),
                                new BigDecimal("4000.00000000"),
                                List.of(new SolicitudLineaOrdenCompra(
                                        productId, 1, new BigDecimal("10.0000")))),
                        actorId,
                        Set.of("US")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.status())
                                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        var linea = orden.lineas().get(0);
        comprasService.enviarOrden(orden.orden().id(), actorId, Set.of("CO"));
        var bodegaUs = inventarioService.crearBodega(
                new SolicitudBodega(
                        "E54-US-B-" + suffix,
                        "Warehouse integration",
                        null,
                        usCountryId,
                        true),
                actorId,
                Set.of());
        assertThatThrownBy(() -> comprasService.recibir(
                        new SolicitudEntradaInventario(
                                orden.orden().id(),
                                bodegaUs.id(),
                                "E54-FAC-US-" + suffix,
                                new BigDecimal("4100.00000000"),
                                List.of(new SolicitudLineaEntradaInventario(
                                        orden.lineas().get(0).id(), 1))),
                        actorId,
                        Set.of()))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.messageKey())
                                .isEqualTo("error.inventory.receipt.warehouse.country"));
        var primeraEntrada = comprasService.recibir(
                new SolicitudEntradaInventario(
                        orden.orden().id(),
                        bodega.id(),
                        "E54-FAC-A-" + suffix,
                        new BigDecimal("4100.00000000"),
                        List.of(new SolicitudLineaEntradaInventario(linea.id(), 2))),
                actorId,
                Set.of("CO"));
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
                actorId,
                Set.of("CO"));
        assertThatThrownBy(() -> comprasService.obtenerEntrada(
                        primeraEntrada.entrada().id(), Set.of("US")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.status())
                                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));

        inventarioService.registrarSalida(
                new SolicitudSalidaInventario(
                        productId,
                        bodega.id(),
                        "PERDIDA",
                        1,
                        proveedor.id(),
                        "REP-" + suffix,
                        "Prueba de salida"),
                actorId,
                Set.of("CO"));
        var ajuste = ajustesService.solicitar(
                "OPERATIVO",
                new SolicitudAjusteInventario(
                        productId, bodega.id(), 3, "Conteo físico de integración"),
                actorId,
                Set.of("CO"));
        assertThat(ajustesService.listar(
                null, null, productId, bodega.id(), Set.of("US"), PageRequest.of(0, 10))
                .getContent())
                .isEmpty();
        assertThatThrownBy(() -> ajustesService.obtener(ajuste.id(), Set.of("US")))
                .isInstanceOfSatisfying(
                ErrorAplicacionException.class,
                exception -> assertThat(exception.status())
                        .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        mockMvc.perform(get("/api/v1/inventario/ajustes/{id}", ajuste.id())
                        .with(authentication(authenticationActor(
                        actorId,
                        "ENCARGADO_INVENTARIO",
                        "INVENTARIO_CONSULTAR",
                        Set.of("US")))))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> ajustesService.aprobar(
                ajuste.id(), actorId, Set.of("US")))
                .isInstanceOfSatisfying(
                ErrorAplicacionException.class,
                exception -> assertThat(exception.status())
                        .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> ajustesService.solicitar(
                "OPERATIVO",
                new SolicitudAjusteInventario(
                        productId, bodega.id(), 2, "Fuera del alcance"),
                actorId,
                Set.of("US")))
                .isInstanceOfSatisfying(
                ErrorAplicacionException.class,
                exception -> assertThat(exception.status())
                        .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        ajustesService.aprobar(ajuste.id(), actorId, Set.of("CO"));
        var aplicado = ajustesService.aplicar(ajuste.id(), actorId, Set.of("CO"));
        assertThat(ajustesService.listar(
                "APLICADO",
                "OPERATIVO",
                productId,
                bodega.id(),
                Set.of("CO"),
                PageRequest.of(0, 10))
                .getContent())
                .extracting("id")
                .containsExactly(ajuste.id());
        UUID copId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.moneda WHERE codigo_iso = 'COP'",
                UUID.class);
        // La venta toma la fecha local del país: el precio debe estar vigente ese día
        // aunque el equipo (por ejemplo, el CI en UTC) ya esté en el día siguiente.
        String zonaPais = jdbcTemplate.queryForObject(
                "SELECT zona_horaria FROM public.pais WHERE id = ?",
                String.class,
                countryId);
        jdbcTemplate.update(
                """
                INSERT INTO public.precio_producto
                    (id, producto_id, moneda_id, importe, vigente_desde, vigente_hasta)
                VALUES (?, ?, ?, ?, ?, NULL)
                """,
                UUID.randomUUID(),
                productId,
                copId,
                new BigDecimal("100.0000"),
                java.time.LocalDate.now(java.time.ZoneId.of(zonaPais)));
        SolicitudVenta solicitudVenta = new SolicitudVenta(
                null,
                countryId,
                copId,
                List.of(new SolicitudLineaVenta(productId, bodega.id(), 1)));
        assertThatThrownBy(() -> ventasService.crear(
                        solicitudVenta, actorId, Set.of("US")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.status())
                                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        var pendiente = ventasService.crear(solicitudVenta, actorId, Set.of("CO"));
        assertThat(pendiente.venta().estado()).isEqualTo("PENDIENTE_DE_PAGO");
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT cantidad FROM public.saldo_inventario
                        WHERE producto_id = ? AND bodega_id = ?
                        """,
                        Integer.class,
                        productId,
                        bodega.id()))
                .isEqualTo(3);
        var confirmada = ventasService.confirmar(
                pendiente.venta().id(), "EFECTIVO", actorId, Set.of("CO"));
        assertThat(confirmada.venta().estado()).isEqualTo("CONFIRMADA");
        assertThatThrownBy(() -> ventasService.obtener(
                        pendiente.venta().id(), Set.of("US")))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.status())
                                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThat(inventarioService.listarSaldos(
                        productId,
                        null,
                        null,
                        Set.of("US"),
                        true,
                        PageRequest.of(0, 10))
                        .getTotalElements())
                .isZero();
        assertThat(inventarioService.listarMovimientos(
                        productId,
                        bodega.id(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Set.of("US"),
                        PageRequest.of(0, 10))
                        .getTotalElements())
                .isZero();

        assertThat(inventarioService.listarMovimientos(
                        productId,
                        bodega.id(),
                        actorId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(5);
        assertThat(inventarioService.listarMovimientos(
                        productId,
                        bodega.id(),
                        actorId,
                        null,
                        null,
                        null,
                        countryId,
                        copId,
                        PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);

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
        assertThat(balance).isEqualTo(2);
        assertThat(movimientos).isEqualTo(5);
        assertThat(auditorias).isGreaterThanOrEqualTo(6);
    }

    private UsernamePasswordAuthenticationToken authenticationActor(
            UUID actorId,
            String rol,
            String permiso) {
        return authenticationActor(actorId, rol, permiso, Set.of("CO"));
    }

    private UsernamePasswordAuthenticationToken authenticationActor(
            UUID actorId,
            String rol,
            String permiso,
            Set<String> paises) {
        UsuarioAutenticado actor = new UsuarioAutenticado(
                actorId,
                "integracion@local",
                "Usuario de integración",
                "es",
                List.of(rol),
                Set.of(permiso),
                paises,
                UUID.randomUUID());
        return UsernamePasswordAuthenticationToken.authenticated(
                actor,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + rol),
                        new SimpleGrantedAuthority(permiso)));
    }
}
