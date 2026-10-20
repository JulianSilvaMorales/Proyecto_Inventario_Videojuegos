package com.inventario.controller;

import com.inventario.domain.TipoCambio;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.SolicitudTipoCambio;
import com.inventario.service.ConfiguracionService;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/v1/configuracion/tipos-cambio")
@PreAuthorize("hasAuthority('TIPOS_CAMBIO_GESTIONAR')")
public class TiposCambioController {

    private final ConfiguracionService configuracionService;

    public TiposCambioController(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    @GetMapping
    public Page<TipoCambio> listar(
            @RequestParam(required = false) String buscar,
            Pageable pageable) {
        return configuracionService.listarTiposCambio(buscar, pageable);
    }

    @GetMapping("/{id}")
    public TipoCambio obtener(@PathVariable UUID id) {
        return configuracionService.obtenerTipoCambio(id);
    }

    @PostMapping
    public ResponseEntity<TipoCambio> crear(
            @Valid @RequestBody SolicitudTipoCambio solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        TipoCambio tipoCambio = configuracionService.crearTipoCambio(
                solicitud,
                actor.usuarioId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(tipoCambio.id())
                .toUri();
        return ResponseEntity.created(location).body(tipoCambio);
    }

    @PutMapping("/{id}")
    public TipoCambio actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudTipoCambio solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return configuracionService.actualizarTipoCambio(
                id,
                solicitud,
                actor.usuarioId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        configuracionService.eliminarTipoCambio(id, actor.usuarioId());
        return ResponseEntity.noContent().build();
    }
}
