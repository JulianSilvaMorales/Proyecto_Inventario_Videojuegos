package com.inventario.controller;

import com.inventario.dto.ApiInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Sistema", description = "Información general de la API")
public class ApiController {

    @GetMapping
    @Operation(summary = "Consultar versión y estado de la API")
    public ApiInfoResponse info() {
        return new ApiInfoResponse("1", "Sistema de inventario para tienda de videojuegos");
    }
}
