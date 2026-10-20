package com.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SolicitudIdioma(
        @NotBlank
        @Pattern(regexp = "[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")
        @Size(max = 35) String codigoBcp47,
        @NotBlank @Size(max = 120) String nombre,
        @NotNull Boolean habilitado) {
}
