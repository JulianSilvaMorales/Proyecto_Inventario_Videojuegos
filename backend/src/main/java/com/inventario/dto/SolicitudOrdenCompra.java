package com.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SolicitudOrdenCompra(
        @NotNull UUID proveedorId,
        @NotNull @DecimalMin("0.00000001") @Digits(integer = 11, fraction = 8)
                BigDecimal tipoCambio,
        @NotEmpty List<@Valid SolicitudLineaOrdenCompra> lineas) {
}
