package com.inventario.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SolicitudAjusteInventario(
        @NotNull UUID productoId,
        @NotNull UUID bodegaId,
        @Min(0) int cantidadContada,
        @NotBlank @Size(max = 1000) String motivo) {
}
