package com.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SolicitudTipoCambio(
        @NotNull UUID monedaOrigenId,
        @NotNull UUID monedaDestinoId,
        @NotNull @DecimalMin(value = "0.00000001") @Digits(integer = 11, fraction = 8)
        BigDecimal tasa,
        @NotNull Instant vigenteDesdeUtc) {
}
