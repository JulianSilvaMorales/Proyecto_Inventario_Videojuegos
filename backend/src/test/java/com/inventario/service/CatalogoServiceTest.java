package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.domain.Categoria;
import com.inventario.domain.DefinicionAtributo;
import com.inventario.domain.PrecioProducto;
import com.inventario.domain.Producto;
import com.inventario.domain.ProductoTraduccion;
import com.inventario.domain.TipoProducto;
import com.inventario.dto.SolicitudDefinicionAtributo;
import com.inventario.dto.SolicitudPrecioProducto;
import com.inventario.dto.SolicitudProducto;
import com.inventario.dto.SolicitudProductoTraduccion;
import com.inventario.repository.CatalogoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock
    private CatalogoRepository repository;

    @Mock
    private AuditoriaService auditoriaService;

    private CatalogoService service;
    private UUID typeId;
    private UUID categoryId;
    private UUID subcategoryId;
    private UUID actorId;
    private DefinicionAtributo requiredPlatform;

    @BeforeEach
    void setUp() {
        service = new CatalogoService(
                repository,
                auditoriaService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        typeId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        subcategoryId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        requiredPlatform = new DefinicionAtributo(
                UUID.randomUUID(),
                typeId,
                "plataforma",
                "TEXTO",
                true,
                json("[\"PS5\", \"Xbox\"]"));
    }

    @Test
    void creaProductoConAtributosDefinidosTraduccionYPrecio() {
        configurarReferenciasProducto();
        AtomicReference<Producto> guardado = new AtomicReference<>();
        when(repository.crearProducto(any()))
                .thenAnswer(invocation -> {
                    Producto producto = invocation.getArgument(0);
                    guardado.set(producto);
                    return producto.id();
                });
        when(repository.buscarProducto(any()))
                .thenAnswer(invocation -> Optional.of(guardado.get()));
        UUID translationId = UUID.randomUUID();
        when(repository.idiomaHabilitado(any())).thenReturn(true);
        when(repository.crearTraduccion(any())).thenReturn(translationId);
        when(repository.buscarTraduccion(eq(translationId), any()))
                .thenAnswer(invocation -> Optional.of(new ProductoTraduccion(
                        translationId,
                        invocation.getArgument(1),
                        UUID.randomUUID(),
                        "es",
                        "Consola",
                        null)));
        UUID priceId = UUID.randomUUID();
        when(repository.monedaExiste(any())).thenReturn(true);
        when(repository.precioSeSuperpone(
                        any(), any(), any(), nullable(LocalDate.class)))
                .thenReturn(false);
        when(repository.crearPrecio(any())).thenReturn(priceId);
        when(repository.listarTraducciones(any()))
                .thenAnswer(invocation -> List.of(new ProductoTraduccion(
                        translationId,
                        invocation.getArgument(0),
                        UUID.randomUUID(),
                        "es",
                        "Consola",
                        null)));
        when(repository.listarPrecios(any()))
                .thenAnswer(invocation -> List.of(new PrecioProducto(
                        priceId,
                        invocation.getArgument(0),
                        UUID.randomUUID(),
                        "COP",
                        new java.math.BigDecimal("1500000"),
                        LocalDate.of(2026, 1, 1),
                        null)));

        var created = service.crearProducto(solicitud("{\"plataforma\":\"PS5\"}"), actorId);

        assertThat(created.producto().codigoInterno()).startsWith("PRD-");
        assertThat(created.producto().activo()).isTrue();
        assertThat(created.traducciones()).hasSize(1);
        assertThat(created.precios()).hasSize(1);
        verify(repository).crearProducto(any());
        verify(auditoriaService).registrar(
                eq(actorId),
                eq(null),
                eq("CREAR"),
                eq("producto"),
                eq(guardado.get().id()),
                eq(null),
                any());
    }

    @Test
    void rechazaAtributosObligatoriosAusentes() {
        configurarReferenciasProducto();

        assertThatThrownBy(() -> service.crearProducto(solicitud("{}"), actorId))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> {
                            assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(exception.messageKey())
                                    .isEqualTo("error.catalog.product.attribute-required");
                        });

        verify(repository, never()).crearProducto(any());
    }

    @Test
    void rechazaAtributosNoDefinidosYValoresFueraDeOpciones() {
        configurarReferenciasProducto();

        assertThatThrownBy(() -> service.crearProducto(
                solicitud("{\"plataforma\":\"PS5\", \"extra\":\"dato\"}"),
                actorId))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.messageKey())
                                .isEqualTo("error.catalog.product.attribute-unknown"));

        assertThatThrownBy(() -> service.crearProducto(
                solicitud("{\"plataforma\":\"Switch\"}"),
                actorId))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.messageKey())
                                .isEqualTo("error.catalog.product.attribute-option"));

        verify(repository, never()).crearProducto(any());
    }

    @Test
    void rechazaValorQueNoCoincideConElTipoDeDato() {
        configurarReferenciasProducto();

        assertThatThrownBy(() -> service.crearProducto(
                solicitud("{\"plataforma\":true}"),
                actorId))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> assertThat(exception.messageKey())
                                .isEqualTo("error.catalog.product.attribute-type"));

        verify(repository, never()).crearProducto(any());
    }

    @Test
    void definicionConOpcionesNulasEnElJsonSeGuardaSinOpciones() {
        when(repository.buscarTipoProducto(typeId))
                .thenReturn(Optional.of(new TipoProducto(typeId, "VIDEOJUEGO", "Videojuego", true)));
        AtomicReference<DefinicionAtributo> guardada = new AtomicReference<>();
        UUID id = UUID.randomUUID();
        when(repository.crearDefinicion(any())).thenAnswer(invocation -> {
            DefinicionAtributo definicion = invocation.getArgument(0);
            guardada.set(new DefinicionAtributo(
                    id, definicion.tipoProductoId(), definicion.nombre(), definicion.tipoDato(),
                    definicion.obligatorio(), definicion.opciones()));
            return id;
        });
        when(repository.buscarDefinicion(id)).thenAnswer(invocation -> Optional.of(guardada.get()));

        DefinicionAtributo creada = service.crearDefinicion(
                new SolicitudDefinicionAtributo(
                        typeId, "distribuidor", "TEXTO", true, OBJECT_MAPPER.nullNode()),
                actorId);

        assertThat(creada.opciones()).isNull();
    }

    @Test
    void desactivarProductoActualizaEstadoSinEliminarlo() {
        UUID productId = UUID.randomUUID();
        Producto active = producto(productId, true);
        Producto inactive = producto(productId, false);
        when(repository.buscarProducto(productId))
                .thenReturn(Optional.of(active), Optional.of(inactive));
        when(repository.desactivarProducto(eq(productId), any())).thenReturn(true);
        when(repository.listarTraducciones(productId)).thenReturn(List.of());
        when(repository.listarPrecios(productId)).thenReturn(List.of());

        var result = service.desactivarProducto(productId, actorId);

        assertThat(result.producto().activo()).isFalse();
        verify(repository).desactivarProducto(eq(productId), any());
    }

    @Test
    void rechazaPrecioConVigenciaSuperpuesta() {
        UUID productId = UUID.randomUUID();
        UUID currencyId = UUID.randomUUID();
        when(repository.buscarProducto(productId)).thenReturn(Optional.of(producto(productId, true)));
        when(repository.monedaExiste(currencyId)).thenReturn(true);
        when(repository.precioSeSuperpone(
                        eq(productId),
                        eq(currencyId),
                        eq(LocalDate.of(2026, 1, 1)),
                        nullable(LocalDate.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.agregarPrecio(
                productId,
                new SolicitudPrecioProducto(
                        currencyId,
                        new java.math.BigDecimal("10.00"),
                        LocalDate.of(2026, 1, 1),
                        null),
                actorId))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        exception -> {
                            assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(exception.messageKey())
                                    .isEqualTo("error.catalog.price.overlap");
                        });

        verify(repository).bloquearProducto(productId);
        verify(repository, never()).crearPrecio(any());
    }

    private void configurarReferenciasProducto() {
        when(repository.buscarTipoProducto(typeId))
                .thenReturn(Optional.of(new TipoProducto(typeId, "CONSOLA", "Consola", true)));
        when(repository.buscarCategoria(categoryId))
                .thenReturn(Optional.of(new Categoria(categoryId, "VIDEOJUEGOS", "Videojuegos", null, true)));
        when(repository.buscarCategoria(subcategoryId))
                .thenReturn(Optional.of(new Categoria(
                        subcategoryId, "CONSOLA", "Consola", categoryId, true)));
        when(repository.listarDefinicionesPorTipo(typeId)).thenReturn(List.of(requiredPlatform));
    }

    private SolicitudProducto solicitud(String attributes) {
        return new SolicitudProducto(
                typeId,
                categoryId,
                subcategoryId,
                json(attributes),
                List.of(new SolicitudProductoTraduccion(
                        UUID.randomUUID(),
                        "Consola",
                        null)),
                List.of(new SolicitudPrecioProducto(
                        UUID.randomUUID(),
                        new java.math.BigDecimal("1500000"),
                        LocalDate.of(2026, 1, 1),
                        null)));
    }

    private Producto producto(UUID id, boolean active) {
        return new Producto(
                id,
                "PRD-" + id.toString().toUpperCase(),
                typeId,
                categoryId,
                subcategoryId,
                json("{\"plataforma\":\"PS5\"}"),
                active,
                NOW,
                NOW);
    }

    private static JsonNode json(String value) {
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
