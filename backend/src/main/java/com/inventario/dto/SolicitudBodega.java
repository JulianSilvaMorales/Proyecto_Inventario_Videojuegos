package com.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SolicitudBodega(
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String codigo,
        @NotBlank @Size(max = 160) String nombre,
        UUID categoriaId,
        @NotNull Boolean activo) {
}
