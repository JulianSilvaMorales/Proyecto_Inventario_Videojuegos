package com.inventario.controller;

import com.inventario.domain.DetalleEntradaInventario;
import com.inventario.domain.DetalleOrdenCompra;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.SolicitudEntradaInventario;
import com.inventario.dto.SolicitudOrdenCompra;
import com.inventario.service.ComprasService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/compras")
@PreAuthorize("hasAuthority('COMPRAS_GESTIONAR')")
public class ComprasController {

    private final ComprasService comprasService;

    public ComprasController(ComprasService comprasService) {
        this.comprasService = comprasService;
    }

    @GetMapping("/ordenes")
    public Page<com.inventario.domain.OrdenCompra> listarOrdenes(
            @RequestParam(required = false) UUID proveedorId,
            @RequestParam(required = false) String estado,
            Pageable pageable) {
        return comprasService.listarOrdenes(proveedorId, estado, pageable);
    }

    @GetMapping("/ordenes/{id}")
    public DetalleOrdenCompra obtenerOrden(@PathVariable UUID id) {
        return comprasService.obtenerOrden(id);
    }

    @PostMapping("/ordenes")
    public ResponseEntity<DetalleOrdenCompra> crearOrden(
            @Valid @RequestBody SolicitudOrdenCompra solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        DetalleOrdenCompra orden = comprasService.crearOrden(solicitud, actor.usuarioId());
        return creado("/ordenes", orden.orden().id(), orden);
    }

    @PostMapping("/ordenes/{id}/enviar")
    public DetalleOrdenCompra enviarOrden(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return comprasService.enviarOrden(id, actor.usuarioId());
    }

    @PostMapping("/ordenes/{id}/cancelar")
    public DetalleOrdenCompra cancelarOrden(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return comprasService.cancelarOrden(id, actor.usuarioId());
    }

    @PostMapping("/entradas")
    public ResponseEntity<DetalleEntradaInventario> recibir(
            @Valid @RequestBody SolicitudEntradaInventario solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        DetalleEntradaInventario entrada =
                comprasService.recibir(solicitud, actor.usuarioId());
        return creado("/entradas", entrada.entrada().id(), entrada);
    }

    @GetMapping("/entradas/{id}")
    public DetalleEntradaInventario obtenerEntrada(@PathVariable UUID id) {
        return comprasService.obtenerEntrada(id);
    }

    private <T> ResponseEntity<T> creado(String path, UUID id, T body) {
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/v1/compras" + path)
                .replaceQuery(null)
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(body);
    }
}
