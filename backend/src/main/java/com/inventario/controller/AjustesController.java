package com.inventario.controller;

import com.inventario.domain.AjusteInventario;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.SolicitudAjusteInventario;
import com.inventario.service.AjustesService;
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
@RequestMapping("/api/v1/inventario/ajustes")
@PreAuthorize("hasAuthority('INVENTARIO_CONSULTAR')")
public class AjustesController {

    private final AjustesService ajustesService;

    public AjustesController(AjustesService ajustesService) {
        this.ajustesService = ajustesService;
    }

    @GetMapping
    public Page<AjusteInventario> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) UUID productoId,
            @RequestParam(required = false) UUID bodegaId,
            @AuthenticationPrincipal UsuarioAutenticado actor,
            Pageable pageable) {
        return ajustesService.listar(
                estado, tipo, productoId, bodegaId, actor.paises(), pageable);
    }

    @GetMapping("/{id}")
    public AjusteInventario obtener(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ajustesService.obtener(id, actor.paises());
    }

    @PostMapping("/operativos")
    @PreAuthorize("hasAuthority('AJUSTE_OPERATIVO_SOLICITAR') and hasRole('ENCARGADO_INVENTARIO')")
    public ResponseEntity<AjusteInventario> solicitarOperativo(
            @Valid @RequestBody SolicitudAjusteInventario solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        AjusteInventario ajuste =
                ajustesService.solicitar(
                        "OPERATIVO", solicitud, actor.usuarioId(), actor.paises());
        return creado(ajuste.id(), ajuste);
    }

    @PostMapping("/tecnicos")
    @PreAuthorize("hasAuthority('AJUSTE_TECNICO_SOLICITAR') and hasRole('EXPERTO_TECNICO')")
    public ResponseEntity<AjusteInventario> solicitarTecnico(
            @Valid @RequestBody SolicitudAjusteInventario solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        AjusteInventario ajuste =
                ajustesService.solicitar(
                        "TECNICO", solicitud, actor.usuarioId(), actor.paises());
        return creado(ajuste.id(), ajuste);
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasAuthority('AJUSTES_APROBAR')")
    public AjusteInventario aprobar(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ajustesService.aprobar(id, actor.usuarioId(), actor.paises());
    }

    @PostMapping("/{id}/aplicar")
    @PreAuthorize("hasAuthority('AJUSTES_APROBAR')")
    public AjusteInventario aplicar(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ajustesService.aplicar(id, actor.usuarioId(), actor.paises());
    }

    private ResponseEntity<AjusteInventario> creado(UUID id, AjusteInventario body) {
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/v1/inventario/ajustes/{id}")
                .replaceQuery(null)
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    @PostMapping("/{id}/revalidar")
    @PreAuthorize("hasAuthority('AJUSTES_APROBAR')")
    public AjusteInventario revalidar(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ajustesService.revalidar(id, actor.usuarioId(), actor.paises());
    }
}
