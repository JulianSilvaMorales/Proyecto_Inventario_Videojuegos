package com.inventario.controller;

import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.RespuestaVenta;
import com.inventario.dto.SolicitudConfirmacionVenta;
import com.inventario.dto.SolicitudVenta;
import com.inventario.service.VentasService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/ventas")
@PreAuthorize("hasAuthority('VENTAS_GESTIONAR')")
public class VentasController {

    private final VentasService ventasService;

    public VentasController(VentasService ventasService) {
        this.ventasService = ventasService;
    }

    @PostMapping
    public ResponseEntity<RespuestaVenta> crear(
            @Valid @RequestBody SolicitudVenta solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        RespuestaVenta venta = ventasService.crear(
                solicitud, actor.usuarioId(), actor.paises());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(venta.venta().id())
                .toUri();
        return ResponseEntity.created(location).body(venta);
    }

    @GetMapping("/{id}")
    public RespuestaVenta obtener(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ventasService.obtener(id, actor.paises());
    }

    @GetMapping("/{id}/recibo")
    public RespuestaVenta obtenerRecibo(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ventasService.obtenerRecibo(id, actor.paises());
    }

    @PostMapping("/{id}/confirmar")
    public RespuestaVenta confirmar(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudConfirmacionVenta solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ventasService.confirmar(
                id, solicitud.medioPago(), actor.usuarioId(), actor.paises());
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('VENTAS_ANULAR_PENDIENTES')")
    public RespuestaVenta anular(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ventasService.anular(id, actor.usuarioId(), actor.paises());
    }
}
