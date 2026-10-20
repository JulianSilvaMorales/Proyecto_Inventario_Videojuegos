package com.inventario.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventario.domain.Categoria;
import com.inventario.domain.DefinicionAtributo;
import com.inventario.domain.DetalleProducto;
import com.inventario.domain.PrecioProducto;
import com.inventario.domain.Producto;
import com.inventario.domain.ProductoTraduccion;
import com.inventario.domain.TipoProducto;
import com.inventario.dto.SolicitudActualizacionProducto;
import com.inventario.dto.SolicitudCategoria;
import com.inventario.dto.SolicitudDefinicionAtributo;
import com.inventario.dto.SolicitudPrecioProducto;
import com.inventario.dto.SolicitudProducto;
import com.inventario.dto.SolicitudProductoTraduccion;
import com.inventario.dto.SolicitudTipoProducto;
import com.inventario.repository.CatalogoRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogoService {

    private static final Set<String> TIPOS_DATO =
            Set.of("TEXTO", "ENTERO", "DECIMAL", "BOOLEANO", "JSON");

    private final CatalogoRepository repository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public CatalogoService(
            CatalogoRepository repository,
            AuditoriaService auditoriaService,
            Clock clock) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Categoria> listarCategorias(String buscar, Pageable pageable) {
        return repository.listarCategorias(buscar, pageable);
    }

    @Transactional(readOnly = true)
    public Categoria obtenerCategoria(UUID id) {
        return requerir(
                repository.buscarCategoria(id),
                HttpStatus.NOT_FOUND,
                "error.catalog.category.not-found");
    }

    @Transactional
    public Categoria crearCategoria(SolicitudCategoria solicitud, UUID actorId) {
        bloquearCategorias(null, solicitud.padreId());
        Categoria nueva = categoria(null, solicitud);
        UUID id = repository.crearCategoria(nueva);
        Categoria creada = obtenerCategoria(id);
        auditoriaService.registrar(actorId, null, "CREAR", "categoria", id, null, creada);
        return creada;
    }

    @Transactional
    public Categoria actualizarCategoria(UUID id, SolicitudCategoria solicitud, UUID actorId) {
        bloquearCategorias(id, solicitud.padreId());
        Categoria anterior = obtenerCategoria(id);
        Categoria nueva = categoria(id, solicitud);
        validarDesactivacionCategoria(nueva);
        if (!repository.actualizarCategoria(nueva)) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.category.not-found");
        }
        Categoria actualizada = obtenerCategoria(id);
        auditoriaService.registrar(
                actorId, null, "ACTUALIZAR", "categoria", id, anterior, actualizada);
        return actualizada;
    }

    @Transactional
    public Categoria desactivarCategoria(UUID id, UUID actorId) {
        repository.bloquearCategoria(id);
        Categoria anterior = obtenerCategoria(id);
        if (repository.tieneCategoriasHijas(id) || repository.estaCategoriaEnProductoActivo(id)) {
            throw error(HttpStatus.CONFLICT, "error.catalog.category.in-use");
        }
        if (!repository.desactivarCategoria(id)) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.category.not-found");
        }
        Categoria actualizada = obtenerCategoria(id);
        auditoriaService.registrar(
                actorId, null, "DESACTIVAR", "categoria", id, anterior, actualizada);
        return actualizada;
    }

    @Transactional(readOnly = true)
    public Page<TipoProducto> listarTiposProducto(String buscar, Pageable pageable) {
        return repository.listarTiposProducto(buscar, pageable);
    }

    @Transactional(readOnly = true)
    public TipoProducto obtenerTipoProducto(UUID id) {
        return requerir(
                repository.buscarTipoProducto(id),
                HttpStatus.NOT_FOUND,
                "error.catalog.type.not-found");
    }

    @Transactional
    public TipoProducto crearTipoProducto(SolicitudTipoProducto solicitud, UUID actorId) {
        TipoProducto nuevo = tipoProducto(null, solicitud);
        UUID id = repository.crearTipoProducto(nuevo);
        TipoProducto creado = obtenerTipoProducto(id);
        auditoriaService.registrar(actorId, null, "CREAR", "tipo_producto", id, null, creado);
        return creado;
    }

    @Transactional
    public TipoProducto actualizarTipoProducto(
            UUID id,
            SolicitudTipoProducto solicitud,
            UUID actorId) {
        repository.bloquearTipoProducto(id);
        TipoProducto anterior = obtenerTipoProducto(id);
        TipoProducto nuevo = tipoProducto(id, solicitud);
        validarDesactivacionTipo(nuevo);
        if (!repository.actualizarTipoProducto(nuevo)) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.type.not-found");
        }
        TipoProducto actualizado = obtenerTipoProducto(id);
        auditoriaService.registrar(
                actorId, null, "ACTUALIZAR", "tipo_producto", id, anterior, actualizado);
        return actualizado;
    }

    @Transactional
    public TipoProducto desactivarTipoProducto(UUID id, UUID actorId) {
        repository.bloquearTipoProducto(id);
        TipoProducto anterior = obtenerTipoProducto(id);
        if (repository.tieneProductosActivos(id)) {
            throw error(HttpStatus.CONFLICT, "error.catalog.type.in-use");
        }
        if (!repository.desactivarTipoProducto(id)) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.type.not-found");
        }
        TipoProducto actualizado = obtenerTipoProducto(id);
        auditoriaService.registrar(
                actorId, null, "DESACTIVAR", "tipo_producto", id, anterior, actualizado);
        return actualizado;
    }

    @Transactional(readOnly = true)
    public Page<DefinicionAtributo> listarDefiniciones(
            UUID tipoProductoId,
            String buscar,
            Pageable pageable) {
        return repository.listarDefiniciones(tipoProductoId, buscar, pageable);
    }

    @Transactional(readOnly = true)
    public DefinicionAtributo obtenerDefinicion(UUID id) {
        return requerir(
                repository.buscarDefinicion(id),
                HttpStatus.NOT_FOUND,
                "error.catalog.attribute.not-found");
    }

    @Transactional
    public DefinicionAtributo crearDefinicion(
            SolicitudDefinicionAtributo solicitud,
            UUID actorId) {
        repository.bloquearTipoProducto(solicitud.tipoProductoId());
        TipoProducto tipo = obtenerTipoProducto(solicitud.tipoProductoId());
        if (!tipo.activo()) {
            throw error(HttpStatus.BAD_REQUEST, "error.catalog.type.inactive");
        }
        DefinicionAtributo nueva = definicion(null, solicitud);
        UUID id = repository.crearDefinicion(nueva);
        DefinicionAtributo creada = obtenerDefinicion(id);
        auditoriaService.registrar(
                actorId, null, "CREAR", "atributo_definicion", id, null, creada);
        return creada;
    }

    @Transactional
    public DefinicionAtributo actualizarDefinicion(
            UUID id,
            SolicitudDefinicionAtributo solicitud,
            UUID actorId) {
        repository.bloquearDefinicion(id);
        DefinicionAtributo previo = obtenerDefinicion(id);
        bloquearTiposProducto(previo.tipoProductoId(), solicitud.tipoProductoId());
        DefinicionAtributo anterior = obtenerDefinicion(id);
        TipoProducto nuevoTipo = obtenerTipoProducto(solicitud.tipoProductoId());
        if (!nuevoTipo.activo()) {
            throw error(HttpStatus.BAD_REQUEST, "error.catalog.type.inactive");
        }
        DefinicionAtributo nueva = definicion(id, solicitud);
        validarDefinicionContraProductos(anterior, nueva);
        if (!repository.actualizarDefinicion(nueva)) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.attribute.not-found");
        }
        DefinicionAtributo actualizada = obtenerDefinicion(id);
        auditoriaService.registrar(
                actorId, null, "ACTUALIZAR", "atributo_definicion", id, anterior, actualizada);
        return actualizada;
    }

    @Transactional(readOnly = true)
    public Page<Producto> listarProductos(String buscar, Pageable pageable) {
        return repository.listarProductos(buscar, pageable);
    }

    @Transactional(readOnly = true)
    public DetalleProducto obtenerProducto(UUID id) {
        Producto producto = productoExistente(id);
        return detalle(producto);
    }

    @Transactional(readOnly = true)
    public List<ProductoTraduccion> listarTraducciones(UUID productoId) {
        productoExistente(productoId);
        return repository.listarTraducciones(productoId);
    }

    @Transactional(readOnly = true)
    public List<PrecioProducto> listarPrecios(UUID productoId) {
        productoExistente(productoId);
        return repository.listarPrecios(productoId);
    }

    @Transactional
    public DetalleProducto crearProducto(SolicitudProducto solicitud, UUID actorId) {
        bloquearReferenciasProducto(
                solicitud.tipoProductoId(), solicitud.categoriaId(), solicitud.subcategoriaId());
        validarReferenciasProducto(
                solicitud.tipoProductoId(), solicitud.categoriaId(), solicitud.subcategoriaId());
        JsonNode atributos = solicitud.atributos();
        List<DefinicionAtributo> definiciones =
                repository.listarDefinicionesPorTipo(solicitud.tipoProductoId());
        validarAtributos(atributos, definiciones);

        UUID id = UUID.randomUUID();
        var now = clock.instant();
        Producto nuevo = new Producto(
                id,
                "PRD-" + id.toString().toUpperCase(Locale.ROOT),
                solicitud.tipoProductoId(),
                solicitud.categoriaId(),
                solicitud.subcategoriaId(),
                atributos,
                true,
                now,
                now);
        repository.crearProducto(nuevo);

        Set<UUID> idiomas = new HashSet<>();
        for (SolicitudProductoTraduccion traduccion : solicitud.traducciones()) {
            if (!idiomas.add(traduccion.idiomaId())) {
                throw error(HttpStatus.BAD_REQUEST, "error.catalog.translation.duplicate");
            }
            crearTraduccion(id, traduccion, actorId, false);
        }
        for (SolicitudPrecioProducto precio : solicitud.precios()) {
            crearPrecio(id, precio, actorId, false);
        }

        DetalleProducto creado = detalle(productoExistente(id));
        auditoriaService.registrar(actorId, null, "CREAR", "producto", id, null, creado);
        return creado;
    }

    @Transactional
    public DetalleProducto actualizarProducto(
            UUID id,
            SolicitudActualizacionProducto solicitud,
            UUID actorId) {
        repository.bloquearProducto(id);
        Producto anterior = productoActivo(id);
        bloquearTiposProducto(anterior.tipoProductoId(), solicitud.tipoProductoId());
        bloquearCategorias(solicitud.categoriaId(), solicitud.subcategoriaId());
        validarReferenciasProducto(
                solicitud.tipoProductoId(), solicitud.categoriaId(), solicitud.subcategoriaId());
        JsonNode atributos = solicitud.atributos();
        validarAtributos(
                atributos,
                repository.listarDefinicionesPorTipo(solicitud.tipoProductoId()));
        Producto nuevo = new Producto(
                anterior.id(),
                anterior.codigoInterno(),
                solicitud.tipoProductoId(),
                solicitud.categoriaId(),
                solicitud.subcategoriaId(),
                atributos,
                anterior.activo(),
                anterior.creadoEnUtc(),
                clock.instant());
        if (!repository.actualizarProducto(nuevo)) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.product.not-found");
        }
        DetalleProducto actualizado = detalle(productoExistente(id));
        auditoriaService.registrar(
                actorId, null, "ACTUALIZAR", "producto", id, detalle(anterior), actualizado);
        return actualizado;
    }

    @Transactional
    public DetalleProducto desactivarProducto(UUID id, UUID actorId) {
        Producto anterior = productoActivo(id);
        if (!repository.desactivarProducto(id, clock.instant())) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.product.not-found");
        }
        DetalleProducto actualizado = detalle(productoExistente(id));
        auditoriaService.registrar(
                actorId, null, "DESACTIVAR", "producto", id, detalle(anterior), actualizado);
        return actualizado;
    }

    @Transactional
    public ProductoTraduccion agregarTraduccion(
            UUID productoId,
            SolicitudProductoTraduccion solicitud,
            UUID actorId) {
        repository.bloquearProducto(productoId);
        productoActivo(productoId);
        return crearTraduccion(productoId, solicitud, actorId, true);
    }

    @Transactional
    public ProductoTraduccion actualizarTraduccion(
            UUID productoId,
            UUID traduccionId,
            SolicitudProductoTraduccion solicitud,
            UUID actorId) {
        repository.bloquearProducto(productoId);
        productoActivo(productoId);
        ProductoTraduccion anterior = requerir(
                repository.buscarTraduccion(traduccionId, productoId),
                HttpStatus.NOT_FOUND,
                "error.catalog.translation.not-found");
        validarIdioma(solicitud.idiomaId());
        if (!anterior.idiomaId().equals(solicitud.idiomaId())
                && repository.listarTraducciones(productoId).stream()
                        .anyMatch(item -> item.idiomaId().equals(solicitud.idiomaId()))) {
            throw error(HttpStatus.CONFLICT, "error.catalog.translation.duplicate");
        }
        ProductoTraduccion nueva = new ProductoTraduccion(
                traduccionId,
                productoId,
                solicitud.idiomaId(),
                null,
                solicitud.nombre().trim(),
                normalizarDescripcion(solicitud.descripcion()));
        if (!repository.actualizarTraduccion(nueva)) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.translation.not-found");
        }
        ProductoTraduccion actualizada = requerir(
                repository.buscarTraduccion(traduccionId, productoId),
                HttpStatus.NOT_FOUND,
                "error.catalog.translation.not-found");
        auditoriaService.registrar(
                actorId, null, "ACTUALIZAR", "producto_traduccion",
                traduccionId, anterior, actualizada);
        return actualizada;
    }

    @Transactional
    public PrecioProducto agregarPrecio(
            UUID productoId,
            SolicitudPrecioProducto solicitud,
            UUID actorId) {
        repository.bloquearProducto(productoId);
        productoActivo(productoId);
        PrecioProducto precio = crearPrecio(productoId, solicitud, actorId, true);
        return precio;
    }

    private Categoria categoria(UUID id, SolicitudCategoria solicitud) {
        UUID parentId = solicitud.padreId();
        if (parentId != null) {
            Categoria parent = obtenerCategoria(parentId);
            if (!parent.activo()
                    || parent.padreId() != null
                    || parent.id().equals(id)
                    || (id != null && repository.tieneCategoriasHijas(id))) {
                throw error(HttpStatus.BAD_REQUEST, "error.catalog.category.parent");
            }
        } else if (id != null && repository.tieneCategoriasHijas(id)) {
            Categoria actual = obtenerCategoria(id);
            if (actual.padreId() != null) {
                throw error(HttpStatus.BAD_REQUEST, "error.catalog.category.parent");
            }
        }
        return new Categoria(
                id,
                solicitud.codigo().trim().toUpperCase(Locale.ROOT),
                solicitud.nombre().trim(),
                parentId,
                solicitud.activo());
    }

    private void bloquearCategorias(UUID id, UUID parentId) {
        java.util.stream.Stream.of(id, parentId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(repository::bloquearCategoria);
    }

    private void bloquearTiposProducto(UUID... ids) {
        java.util.Arrays.stream(ids)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(repository::bloquearTipoProducto);
    }

    private void bloquearReferenciasProducto(
            UUID tipoProductoId,
            UUID categoriaId,
            UUID subcategoriaId) {
        repository.bloquearTipoProducto(tipoProductoId);
        bloquearCategorias(categoriaId, subcategoriaId);
    }

    private TipoProducto tipoProducto(UUID id, SolicitudTipoProducto solicitud) {
        return new TipoProducto(
                id,
                solicitud.codigo().trim().toUpperCase(Locale.ROOT),
                solicitud.nombre().trim(),
                solicitud.activo());
    }

    private DefinicionAtributo definicion(
            UUID id,
            SolicitudDefinicionAtributo solicitud) {
        String dataType = solicitud.tipoDato().trim().toUpperCase(Locale.ROOT);
        if (!TIPOS_DATO.contains(dataType)) {
            throw error(HttpStatus.BAD_REQUEST, "error.catalog.attribute.type");
        }
        JsonNode options = solicitud.opciones();
        if (options != null && !options.isArray()) {
            throw error(HttpStatus.BAD_REQUEST, "error.catalog.attribute.options");
        }
        if (options != null) {
            for (JsonNode option : options) {
                if (!tipoCompatible(dataType, option)) {
                    throw error(HttpStatus.BAD_REQUEST, "error.catalog.attribute.options");
                }
            }
        }
        return new DefinicionAtributo(
                id,
                solicitud.tipoProductoId(),
                solicitud.nombre().trim(),
                dataType,
                solicitud.obligatorio(),
                options);
    }

    private void validarDefinicionContraProductos(
            DefinicionAtributo anterior,
            DefinicionAtributo nueva) {
        Set<UUID> typeIds = new HashSet<>();
        typeIds.add(anterior.tipoProductoId());
        typeIds.add(nueva.tipoProductoId());
        for (UUID typeId : typeIds) {
            List<DefinicionAtributo> definitions =
                    new ArrayList<>(repository.listarDefinicionesPorTipo(typeId));
            definitions.removeIf(definition -> definition.id().equals(anterior.id()));
            if (typeId.equals(nueva.tipoProductoId())) {
                definitions.add(nueva);
            }
            for (Producto producto : repository.listarProductosActivosPorTipo(typeId)) {
                try {
                    validarAtributos(producto.atributos(), definitions);
                } catch (ErrorAplicacionException exception) {
                    throw error(HttpStatus.CONFLICT, "error.catalog.attribute.in-use");
                }
            }
        }
    }

    private void validarAtributos(
            JsonNode atributos,
            List<DefinicionAtributo> definiciones) {
        if (atributos == null || !atributos.isObject()) {
            throw error(HttpStatus.BAD_REQUEST, "error.catalog.product.attributes-object");
        }
        Map<String, DefinicionAtributo> byName = definiciones.stream()
                .collect(Collectors.toMap(
                        DefinicionAtributo::nombre,
                        Function.identity(),
                        (first, ignored) -> first));
        for (var field : atributos.properties()) {
            DefinicionAtributo definition = byName.get(field.getKey());
            if (definition == null) {
                throw error(HttpStatus.BAD_REQUEST, "error.catalog.product.attribute-unknown");
            }
            JsonNode value = field.getValue();
            if (!tipoCompatible(definition.tipoDato(), value)) {
                throw error(HttpStatus.BAD_REQUEST, "error.catalog.product.attribute-type");
            }
            if (definition.opciones() != null
                    && definition.opciones().isArray()
                    && !contains(definition.opciones(), value)) {
                throw error(HttpStatus.BAD_REQUEST, "error.catalog.product.attribute-option");
            }
        }
        for (DefinicionAtributo definition : definiciones) {
            if (definition.obligatorio()
                    && (!atributos.has(definition.nombre())
                            || atributos.get(definition.nombre()).isNull())) {
                throw error(HttpStatus.BAD_REQUEST, "error.catalog.product.attribute-required");
            }
        }
    }

    private boolean tipoCompatible(String tipoDato, JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        return switch (tipoDato) {
            case "TEXTO" -> value.isTextual();
            case "ENTERO" -> value.isIntegralNumber();
            case "DECIMAL" -> value.isNumber();
            case "BOOLEANO" -> value.isBoolean();
            case "JSON" -> true;
            default -> false;
        };
    }

    private boolean contains(JsonNode options, JsonNode value) {
        for (JsonNode option : options) {
            if (option.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private void validarReferenciasProducto(
            UUID tipoProductoId,
            UUID categoriaId,
            UUID subcategoriaId) {
        TipoProducto tipo = obtenerTipoProducto(tipoProductoId);
        Categoria categoria = obtenerCategoria(categoriaId);
        Categoria subcategoria = obtenerCategoria(subcategoriaId);
        if (!tipo.activo()) {
            throw error(HttpStatus.BAD_REQUEST, "error.catalog.type.inactive");
        }
        if (!categoria.activo()
                || categoria.padreId() != null
                || !subcategoria.activo()
                || !categoria.id().equals(subcategoria.padreId())) {
            throw error(HttpStatus.BAD_REQUEST, "error.catalog.product.category");
        }
    }

    private void validarDesactivacionCategoria(Categoria categoria) {
        if (!categoria.activo()
                && (repository.tieneCategoriasHijas(categoria.id())
                        || repository.estaCategoriaEnProductoActivo(categoria.id()))) {
            throw error(HttpStatus.CONFLICT, "error.catalog.category.in-use");
        }
    }

    private void validarDesactivacionTipo(TipoProducto tipo) {
        if (!tipo.activo() && repository.tieneProductosActivos(tipo.id())) {
            throw error(HttpStatus.CONFLICT, "error.catalog.type.in-use");
        }
    }

    private ProductoTraduccion crearTraduccion(
            UUID productoId,
            SolicitudProductoTraduccion solicitud,
            UUID actorId,
            boolean registrarAuditoria) {
        validarIdioma(solicitud.idiomaId());
        ProductoTraduccion nueva = new ProductoTraduccion(
                null,
                productoId,
                solicitud.idiomaId(),
                null,
                solicitud.nombre().trim(),
                normalizarDescripcion(solicitud.descripcion()));
        UUID id = repository.crearTraduccion(nueva);
        ProductoTraduccion creada = requerir(
                repository.buscarTraduccion(id, productoId),
                HttpStatus.NOT_FOUND,
                "error.catalog.translation.not-found");
        if (registrarAuditoria) {
            auditoriaService.registrar(
                    actorId, null, "CREAR", "producto_traduccion", id, null, creada);
        }
        return creada;
    }

    private PrecioProducto crearPrecio(
            UUID productoId,
            SolicitudPrecioProducto solicitud,
            UUID actorId,
            boolean registrarAuditoria) {
        if (solicitud.vigenteHasta() != null
                && solicitud.vigenteHasta().isBefore(solicitud.vigenteDesde())) {
            throw error(HttpStatus.BAD_REQUEST, "error.catalog.price.dates");
        }
        if (!repository.monedaExiste(solicitud.monedaId())) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.currency.not-found");
        }
        if (repository.precioSeSuperpone(
                productoId,
                solicitud.monedaId(),
                solicitud.vigenteDesde(),
                solicitud.vigenteHasta())) {
            throw error(HttpStatus.CONFLICT, "error.catalog.price.overlap");
        }
        PrecioProducto nuevo = new PrecioProducto(
                null,
                productoId,
                solicitud.monedaId(),
                null,
                solicitud.importe(),
                solicitud.vigenteDesde(),
                solicitud.vigenteHasta());
        UUID id = repository.crearPrecio(nuevo);
        PrecioProducto creado = repository.listarPrecios(productoId).stream()
                .filter(precio -> precio.id().equals(id))
                .findFirst()
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "error.catalog.price.not-found"));
        if (registrarAuditoria) {
            auditoriaService.registrar(
                    actorId, null, "CREAR", "precio_producto", id, null, creado);
        }
        return creado;
    }

    private void validarIdioma(UUID idiomaId) {
        if (!repository.idiomaHabilitado(idiomaId)) {
            throw error(HttpStatus.BAD_REQUEST, "error.catalog.language.not-found");
        }
    }

    private String normalizarDescripcion(String descripcion) {
        return descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
    }

    private Producto productoExistente(UUID id) {
        return requerir(
                repository.buscarProducto(id),
                HttpStatus.NOT_FOUND,
                "error.catalog.product.not-found");
    }

    private Producto productoActivo(UUID id) {
        Producto producto = productoExistente(id);
        if (!producto.activo()) {
            throw error(HttpStatus.CONFLICT, "error.catalog.product.inactive");
        }
        return producto;
    }

    private DetalleProducto detalle(Producto producto) {
        return new DetalleProducto(
                producto,
                repository.listarTraducciones(producto.id()),
                repository.listarPrecios(producto.id()));
    }

    private <T> T requerir(
            java.util.Optional<T> value,
            HttpStatus status,
            String messageKey) {
        return value.orElseThrow(() -> error(status, messageKey));
    }

    private ErrorAplicacionException error(HttpStatus status, String messageKey) {
        return new ErrorAplicacionException(status, messageKey);
    }
}
