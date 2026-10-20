package com.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record SolicitudLineaOrdenCompra(
        @NotNull UUID productoId,
        @Min(1) int cantidadPedida,
        @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4)
                BigDecimal costoUnitario) {
}
