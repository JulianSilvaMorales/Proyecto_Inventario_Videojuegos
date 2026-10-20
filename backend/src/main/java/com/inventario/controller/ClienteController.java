package com.inventario.controller;

import com.inventario.domain.Cliente;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.SolicitudCliente;
import com.inventario.service.ClienteService;
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
@RequestMapping("/api/v1/clientes")
@PreAuthorize("hasAuthority('CLIENTES_GESTIONAR')")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public Page<Cliente> listar(
            @RequestParam(required = false) String buscar,
            @AuthenticationPrincipal UsuarioAutenticado actor,
            Pageable pageable) {
        return clienteService.listar(buscar, actor.paises(), pageable);
    }

    @GetMapping("/{id}")
    public Cliente obtener(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return clienteService.obtener(id, actor.paises());
    }

    @PostMapping
    public ResponseEntity<Cliente> crear(
            @Valid @RequestBody SolicitudCliente solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        Cliente cliente = clienteService.crear(solicitud, actor.usuarioId(), actor.paises());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(cliente.id())
                .toUri();
        return ResponseEntity.created(location).body(cliente);
    }

    @PutMapping("/{id}")
    public Cliente actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudCliente solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return clienteService.actualizar(id, solicitud, actor.usuarioId(), actor.paises());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        clienteService.desactivar(id, actor.usuarioId(), actor.paises());
        return ResponseEntity.noContent().build();
    }
}
