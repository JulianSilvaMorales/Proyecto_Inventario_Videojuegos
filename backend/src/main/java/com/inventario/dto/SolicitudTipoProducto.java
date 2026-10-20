package com.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SolicitudTipoProducto(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,32}") String codigo,
        @NotBlank @Size(max = 120) String nombre,
        @NotNull Boolean activo) {
}
