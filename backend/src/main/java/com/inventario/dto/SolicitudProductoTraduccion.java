package com.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SolicitudProductoTraduccion(
        @NotNull UUID idiomaId,
        @NotBlank @Size(max = 200) String nombre,
        @Size(max = 4000) String descripcion) {
}
