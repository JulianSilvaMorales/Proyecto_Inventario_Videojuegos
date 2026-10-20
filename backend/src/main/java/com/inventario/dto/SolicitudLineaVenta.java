package com.inventario.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SolicitudLineaVenta(
        @NotNull UUID productoId,
        @NotNull UUID bodegaId,
        @Min(1) int cantidad) {
}
