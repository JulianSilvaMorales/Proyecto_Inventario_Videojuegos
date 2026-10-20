package com.inventario.controller;

import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.ReferenciaOpcion;
import com.inventario.service.ReferenciasService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/referencias")
@PreAuthorize("isAuthenticated()")
public class ReferenciasController {

    private final ReferenciasService referenciasService;

    public ReferenciasController(ReferenciasService referenciasService) {
        this.referenciasService = referenciasService;
    }

    @GetMapping("/{nombre}")
    public List<ReferenciaOpcion> listar(
            @PathVariable String nombre,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return referenciasService.listar(nombre, actor);
    }
}
