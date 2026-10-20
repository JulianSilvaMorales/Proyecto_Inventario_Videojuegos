package com.inventario.controller;

import com.inventario.domain.Idioma;
import com.inventario.domain.Impuesto;
import com.inventario.domain.Moneda;
import com.inventario.domain.Pais;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.SolicitudIdioma;
import com.inventario.dto.SolicitudImpuesto;
import com.inventario.dto.SolicitudMoneda;
import com.inventario.dto.SolicitudPais;
import com.inventario.service.ConfiguracionService;
import com.inventario.service.ImpuestoService;
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
@RequestMapping("/api/v1/configuracion")
@PreAuthorize("hasAuthority('PARAMETROS_GESTIONAR')")
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;
    private final ImpuestoService impuestoService;

    public ConfiguracionController(
            ConfiguracionService configuracionService,
            ImpuestoService impuestoService) {
        this.configuracionService = configuracionService;
        this.impuestoService = impuestoService;
    }

    @GetMapping("/paises")
    public Page<Pais> listarPaises(
            @RequestParam(required = false) String buscar,
            Pageable pageable) {
        return configuracionService.listarPaises(buscar, pageable);
    }

    @GetMapping("/paises/{id}")
    public Pais obtenerPais(@PathVariable UUID id) {
        return configuracionService.obtenerPais(id);
    }

    @PostMapping("/paises")
    public ResponseEntity<Pais> crearPais(
            @Valid @RequestBody SolicitudPais solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        Pais pais = configuracionService.crearPais(solicitud, actor.usuarioId());
        return creado(pais.id(), pais);
    }

    @PutMapping("/paises/{id}")
    public Pais actualizarPais(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudPais solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return configuracionService.actualizarPais(id, solicitud, actor.usuarioId());
    }

    @DeleteMapping("/paises/{id}")
    public ResponseEntity<Void> eliminarPais(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        configuracionService.eliminarPais(id, actor.usuarioId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/monedas")
    public Page<Moneda> listarMonedas(
            @RequestParam(required = false) String buscar,
            Pageable pageable) {
        return configuracionService.listarMonedas(buscar, pageable);
    }

    @GetMapping("/monedas/{id}")
    public Moneda obtenerMoneda(@PathVariable UUID id) {
        return configuracionService.obtenerMoneda(id);
    }

    @PostMapping("/monedas")
    public ResponseEntity<Moneda> crearMoneda(
            @Valid @RequestBody SolicitudMoneda solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        Moneda moneda = configuracionService.crearMoneda(solicitud, actor.usuarioId());
        return creado(moneda.id(), moneda);
    }

    @PutMapping("/monedas/{id}")
    public Moneda actualizarMoneda(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudMoneda solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return configuracionService.actualizarMoneda(id, solicitud, actor.usuarioId());
    }

    @DeleteMapping("/monedas/{id}")
    public ResponseEntity<Void> eliminarMoneda(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        configuracionService.eliminarMoneda(id, actor.usuarioId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/idiomas")
    public Page<Idioma> listarIdiomas(
            @RequestParam(required = false) String buscar,
            Pageable pageable) {
        return configuracionService.listarIdiomas(buscar, pageable);
    }

    @GetMapping("/idiomas/{id}")
    public Idioma obtenerIdioma(@PathVariable UUID id) {
        return configuracionService.obtenerIdioma(id);
    }

    @PostMapping("/idiomas")
    public ResponseEntity<Idioma> crearIdioma(
            @Valid @RequestBody SolicitudIdioma solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        Idioma idioma = configuracionService.crearIdioma(solicitud, actor.usuarioId());
        return creado(idioma.id(), idioma);
    }

    @PutMapping("/idiomas/{id}")
    public Idioma actualizarIdioma(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudIdioma solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return configuracionService.actualizarIdioma(id, solicitud, actor.usuarioId());
    }

    @DeleteMapping("/idiomas/{id}")
    public ResponseEntity<Void> eliminarIdioma(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        configuracionService.eliminarIdioma(id, actor.usuarioId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/impuestos")
    public Page<Impuesto> listarImpuestos(
            @RequestParam(required = false) String buscar,
            Pageable pageable) {
        return configuracionService.listarImpuestos(buscar, pageable);
    }

    @GetMapping("/impuestos/{id}")
    public Impuesto obtenerImpuesto(@PathVariable UUID id) {
        return configuracionService.obtenerImpuesto(id);
    }

    @PostMapping("/impuestos")
    public ResponseEntity<Impuesto> crearImpuesto(
            @Valid @RequestBody SolicitudImpuesto solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        Impuesto impuesto = configuracionService.crearImpuesto(solicitud, actor.usuarioId());
        return creado(impuesto.id(), impuesto);
    }

    @PutMapping("/impuestos/{id}")
    public Impuesto actualizarImpuesto(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudImpuesto solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return configuracionService.actualizarImpuesto(id, solicitud, actor.usuarioId());
    }

    @DeleteMapping("/impuestos/{id}")
    public ResponseEntity<Void> eliminarImpuesto(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        configuracionService.eliminarImpuesto(id, actor.usuarioId());
        return ResponseEntity.noContent().build();
    }

    private <T> ResponseEntity<T> creado(UUID id, T body) {
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(body);
    }
}
