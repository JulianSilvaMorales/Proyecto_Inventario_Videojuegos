package com.inventario.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SolicitudSalidaInventario(
        @NotNull UUID productoId,
        @NotNull UUID bodegaId,
        @NotBlank String tipo,
        @Min(1) int cantidad,
        @NotNull UUID proveedorId,
        @NotBlank @Size(max = 120) String numeroReporteProveedor,
        @Size(max = 1000) String observacion) {
}
