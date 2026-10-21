package com.inventario.controller;

import com.inventario.domain.Proveedor;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.ReferenciaOpcion;
import com.inventario.dto.SolicitudProveedor;
import com.inventario.service.InventarioService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/proveedores")
@PreAuthorize("hasAnyAuthority('PROVEEDORES_CONSULTAR', 'PROVEEDORES_GESTIONAR')")
public class ProveedorController {

    private final InventarioService inventarioService;

    public ProveedorController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping
    public Page<Proveedor> listar(
            @RequestParam(required = false) String buscar,
            @AuthenticationPrincipal UsuarioAutenticado actor,
            Pageable pageable) {
        return inventarioService.listarProveedores(buscar, actor.paises(), pageable);
    }

    @GetMapping("/{id}")
    public Proveedor obtener(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return inventarioService.obtenerProveedor(id, actor.paises());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROVEEDORES_GESTIONAR')")
    public ResponseEntity<Proveedor> crear(
            @Valid @RequestBody SolicitudProveedor solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        Proveedor proveedor =
                    inventarioService.crearProveedor(
                            solicitud, actor.usuarioId(), actor.paises());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(proveedor.id())
                .toUri();
        return ResponseEntity.created(location).body(proveedor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROVEEDORES_GESTIONAR')")
    public Proveedor actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudProveedor solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return inventarioService.actualizarProveedor(
                id, solicitud, actor.usuarioId(), actor.paises());
    }

    @GetMapping("/{proveedorId}/productos")
    public List<ReferenciaOpcion> productosOficiales(
            @PathVariable UUID proveedorId,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return inventarioService.listarProductosProveedor(
                proveedorId, LocaleContextHolder.getLocale().getLanguage(), actor.paises());
    }

    @PostMapping("/{proveedorId}/productos/{productoId}")
    @PreAuthorize("hasAuthority('PROVEEDORES_GESTIONAR')")
    public ResponseEntity<Void> vincularProducto(
            @PathVariable UUID proveedorId,
            @PathVariable UUID productoId,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        inventarioService.vincularProductoProveedor(
                proveedorId, productoId, actor.usuarioId(), actor.paises());
        return ResponseEntity.noContent().build();
    }
}
