package com.inventario.controller;

import com.inventario.domain.ParametroFila;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.SolicitudActualizacionParametro;
import com.inventario.dto.SolicitudParametro;
import com.inventario.service.ParametroAdminService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
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
@RequestMapping("/api/v1/parametros")
@PreAuthorize("hasAuthority('PARAMETROS_GESTIONAR')")
public class ParametroAdminController {

    private final ParametroAdminService service;

    public ParametroAdminController(ParametroAdminService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ParametroFila> listar(
            @RequestParam(required = false) String clave,
            @RequestParam(required = false) String ambito,
            @AuthenticationPrincipal UsuarioAutenticado actor,
            org.springframework.data.domain.Pageable pageable) {
        return service.listar(clave, ambito, actor.paises(), pageable);
    }

    @GetMapping("/{id}")
    public ParametroFila obtener(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return service.obtener(id, actor.paises());
    }

    @PostMapping
    public ResponseEntity<ParametroFila> crear(
            @Valid @RequestBody SolicitudParametro solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        ParametroFila parametro = service.crear(solicitud, actor.usuarioId(), actor.paises());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(parametro.id())
                .toUri();
        return ResponseEntity.created(location).body(parametro);
    }

    @PutMapping("/{id}")
    public ParametroFila actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudActualizacionParametro solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return service.actualizar(id, solicitud, actor.usuarioId(), actor.paises());
    }
}
