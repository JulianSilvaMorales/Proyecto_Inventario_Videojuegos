package com.inventario.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SolicitudLineaEntradaInventario(
        @NotNull UUID ordenCompraLineaId,
        @Min(1) int cantidad) {
}
