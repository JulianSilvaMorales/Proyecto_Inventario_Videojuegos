package com.inventario.controller;

import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.RespuestaInicioSesion;
import com.inventario.dto.SolicitudInicioSesion;
import com.inventario.service.AutenticacionService;
import com.inventario.service.SesionTracker;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacionController {

    private final AutenticacionService autenticacionService;
    private final SesionTracker sesionTracker;

    public AutenticacionController(
            AutenticacionService autenticacionService,
            SesionTracker sesionTracker) {
        this.autenticacionService = autenticacionService;
        this.sesionTracker = sesionTracker;
    }

    @PostMapping("/login")
    public ResponseEntity<RespuestaInicioSesion> iniciarSesion(
            @Valid @RequestBody SolicitudInicioSesion solicitud) {
        var result = autenticacionService.iniciarSesion(
                solicitud.correo(),
                solicitud.contrasena());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new RespuestaInicioSesion(
                        "Bearer",
                        result.token(),
                        result.venceEnUtc(),
                        result.usuario()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion(
            @AuthenticationPrincipal UsuarioAutenticado user) {
        sesionTracker.cerrar(user.sesionId());
        return ResponseEntity.noContent()
                .header("Cache-Control", "no-store")
                .build();
    }
}
