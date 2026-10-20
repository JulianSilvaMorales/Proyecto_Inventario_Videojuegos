package com.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SolicitudConversionMoneda(
        @NotNull UUID monedaOrigenId,
        @NotNull UUID monedaDestinoId,
        @NotNull @DecimalMin("0") BigDecimal importe,
        @NotNull Instant fechaUtc) {
}
