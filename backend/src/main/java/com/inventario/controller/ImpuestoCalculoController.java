package com.inventario.controller;

import com.inventario.domain.CalculoImpuesto;
import com.inventario.dto.SolicitudCalculoImpuesto;
import com.inventario.service.ImpuestoService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/impuestos")
@PreAuthorize("hasAuthority('PARAMETROS_GESTIONAR')")
public class ImpuestoCalculoController {

    private final ImpuestoService impuestoService;

    public ImpuestoCalculoController(ImpuestoService impuestoService) {
        this.impuestoService = impuestoService;
    }

    @PostMapping("/calcular")
    public CalculoImpuesto calcular(@Valid @RequestBody SolicitudCalculoImpuesto solicitud) {
        return impuestoService.calcular(
                solicitud.paisId(),
                solicitud.categoriaId(),
                solicitud.monedaId(),
                solicitud.precio(),
                solicitud.fecha());
    }
}
