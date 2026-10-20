package com.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SolicitudCategoria(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,32}") String codigo,
        @NotBlank @Size(max = 120) String nombre,
        UUID padreId,
        @NotNull Boolean activo) {
}
