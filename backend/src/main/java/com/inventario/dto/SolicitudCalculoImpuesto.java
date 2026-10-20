package com.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SolicitudCalculoImpuesto(
        @NotNull UUID paisId,
        UUID categoriaId,
        @NotNull UUID monedaId,
        @NotNull @DecimalMin("0") BigDecimal precio,
        @NotNull LocalDate fecha) {
}
