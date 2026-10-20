package com.inventario.controller;

import com.inventario.domain.ConversionMoneda;
import com.inventario.dto.SolicitudConversionMoneda;
import com.inventario.service.ConversionMonedaService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversiones")
@PreAuthorize("hasAuthority('TIPOS_CAMBIO_GESTIONAR')")
public class ConversionMonedaController {

    private final ConversionMonedaService conversionMonedaService;

    public ConversionMonedaController(ConversionMonedaService conversionMonedaService) {
        this.conversionMonedaService = conversionMonedaService;
    }

    @PostMapping("/moneda")
    public ConversionMoneda convertir(
            @Valid @RequestBody SolicitudConversionMoneda solicitud) {
        return conversionMonedaService.convertir(
                solicitud.monedaOrigenId(),
                solicitud.monedaDestinoId(),
                solicitud.importe(),
                solicitud.fechaUtc());
    }
}
