package com.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record SolicitudVenta(
        UUID clienteId,
        @NotNull UUID paisId,
        @NotNull UUID monedaId,
        @NotEmpty @Size(max = 100) List<@Valid SolicitudLineaVenta> lineas) {
}
