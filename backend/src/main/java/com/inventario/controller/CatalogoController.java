package com.inventario.controller;

import com.inventario.domain.Categoria;
import com.inventario.domain.DefinicionAtributo;
import com.inventario.domain.DetalleProducto;
import com.inventario.domain.PrecioProducto;
import com.inventario.domain.Producto;
import com.inventario.domain.ProductoTraduccion;
import com.inventario.domain.TipoProducto;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.SolicitudActualizacionProducto;
import com.inventario.dto.SolicitudCategoria;
import com.inventario.dto.SolicitudDefinicionAtributo;
import com.inventario.dto.SolicitudPrecioProducto;
import com.inventario.dto.SolicitudProducto;
import com.inventario.dto.SolicitudProductoTraduccion;
import com.inventario.dto.SolicitudTipoProducto;
import com.inventario.service.CatalogoService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/catalogo")
@PreAuthorize("hasAuthority('CATALOGO_CONSULTAR')")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/categorias")
    public Page<Categoria> listarCategorias(
            @RequestParam(required = false) String buscar,
            Pageable pageable) {
        return catalogoService.listarCategorias(buscar, pageable);
    }

    @GetMapping("/categorias/{id}")
    public Categoria obtenerCategoria(@PathVariable UUID id) {
        return catalogoService.obtenerCategoria(id);
    }

    @PostMapping("/categorias")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public ResponseEntity<Categoria> crearCategoria(
            @Valid @RequestBody SolicitudCategoria solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        Categoria categoria = catalogoService.crearCategoria(solicitud, actor.usuarioId());
        return creado("/categorias", categoria.id(), categoria);
    }

    @PutMapping("/categorias/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public Categoria actualizarCategoria(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudCategoria solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return catalogoService.actualizarCategoria(id, solicitud, actor.usuarioId());
    }

    @DeleteMapping("/categorias/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public Categoria desactivarCategoria(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return catalogoService.desactivarCategoria(id, actor.usuarioId());
    }

    @GetMapping("/tipos-producto")
    public Page<TipoProducto> listarTiposProducto(
            @RequestParam(required = false) String buscar,
            Pageable pageable) {
        return catalogoService.listarTiposProducto(buscar, pageable);
    }

    @GetMapping("/tipos-producto/{id}")
    public TipoProducto obtenerTipoProducto(@PathVariable UUID id) {
        return catalogoService.obtenerTipoProducto(id);
    }

    @PostMapping("/tipos-producto")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public ResponseEntity<TipoProducto> crearTipoProducto(
            @Valid @RequestBody SolicitudTipoProducto solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        TipoProducto tipo = catalogoService.crearTipoProducto(solicitud, actor.usuarioId());
        return creado("/tipos-producto", tipo.id(), tipo);
    }

    @PutMapping("/tipos-producto/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public TipoProducto actualizarTipoProducto(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudTipoProducto solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return catalogoService.actualizarTipoProducto(id, solicitud, actor.usuarioId());
    }

    @DeleteMapping("/tipos-producto/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public TipoProducto desactivarTipoProducto(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return catalogoService.desactivarTipoProducto(id, actor.usuarioId());
    }

    @GetMapping("/definiciones-atributos")
    public Page<DefinicionAtributo> listarDefiniciones(
            @RequestParam(required = false) UUID tipoProductoId,
            @RequestParam(required = false) String buscar,
            Pageable pageable) {
        return catalogoService.listarDefiniciones(tipoProductoId, buscar, pageable);
    }

    @GetMapping("/definiciones-atributos/{id}")
    public DefinicionAtributo obtenerDefinicion(@PathVariable UUID id) {
        return catalogoService.obtenerDefinicion(id);
    }

    @PostMapping("/definiciones-atributos")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public ResponseEntity<DefinicionAtributo> crearDefinicion(
            @Valid @RequestBody SolicitudDefinicionAtributo solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        DefinicionAtributo definicion =
                catalogoService.crearDefinicion(solicitud, actor.usuarioId());
        return creado("/definiciones-atributos", definicion.id(), definicion);
    }

    @PutMapping("/definiciones-atributos/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public DefinicionAtributo actualizarDefinicion(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudDefinicionAtributo solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return catalogoService.actualizarDefinicion(id, solicitud, actor.usuarioId());
    }

    @GetMapping("/productos")
    public Page<Producto> listarProductos(
            @RequestParam(required = false) String buscar,
            Pageable pageable) {
        return catalogoService.listarProductos(buscar, pageable);
    }

    @GetMapping("/productos/{id}")
    public DetalleProducto obtenerProducto(@PathVariable UUID id) {
        return catalogoService.obtenerProducto(id);
    }

    @PostMapping("/productos")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public ResponseEntity<DetalleProducto> crearProducto(
            @Valid @RequestBody SolicitudProducto solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        DetalleProducto producto = catalogoService.crearProducto(solicitud, actor.usuarioId());
        return creado("/productos", producto.producto().id(), producto);
    }

    @PutMapping("/productos/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public DetalleProducto actualizarProducto(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudActualizacionProducto solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return catalogoService.actualizarProducto(id, solicitud, actor.usuarioId());
    }

    @DeleteMapping("/productos/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public DetalleProducto desactivarProducto(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return catalogoService.desactivarProducto(id, actor.usuarioId());
    }

    @GetMapping("/productos/{id}/traducciones")
    public List<ProductoTraduccion> listarTraducciones(@PathVariable UUID id) {
        return catalogoService.listarTraducciones(id);
    }

    @PostMapping("/productos/{id}/traducciones")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public ResponseEntity<ProductoTraduccion> agregarTraduccion(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudProductoTraduccion solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        ProductoTraduccion traduccion =
                catalogoService.agregarTraduccion(id, solicitud, actor.usuarioId());
        return creado("/productos/" + id + "/traducciones", traduccion.id(), traduccion);
    }

    @PutMapping("/productos/{id}/traducciones/{traduccionId}")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public ProductoTraduccion actualizarTraduccion(
            @PathVariable UUID id,
            @PathVariable UUID traduccionId,
            @Valid @RequestBody SolicitudProductoTraduccion solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return catalogoService.actualizarTraduccion(
                id, traduccionId, solicitud, actor.usuarioId());
    }

    @GetMapping("/productos/{id}/precios")
    public List<PrecioProducto> listarPrecios(@PathVariable UUID id) {
        return catalogoService.listarPrecios(id);
    }

    @PostMapping("/productos/{id}/precios")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public ResponseEntity<PrecioProducto> agregarPrecio(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudPrecioProducto solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        PrecioProducto precio = catalogoService.agregarPrecio(id, solicitud, actor.usuarioId());
        return creado("/productos/" + id + "/precios", precio.id(), precio);
    }

    private <T> ResponseEntity<T> creado(String path, UUID id, T body) {
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/v1/catalogo" + path)
                .replaceQuery(null)
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(body);
    }
}
