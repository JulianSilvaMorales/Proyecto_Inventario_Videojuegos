package com.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SolicitudPrecioProducto(
        @NotNull UUID monedaId,
        @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal importe,
        @NotNull LocalDate vigenteDesde,
        LocalDate vigenteHasta) {
}
