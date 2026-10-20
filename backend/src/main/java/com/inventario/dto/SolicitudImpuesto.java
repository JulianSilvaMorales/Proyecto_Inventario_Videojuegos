package com.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SolicitudImpuesto(
        @NotNull UUID paisId,
        UUID categoriaId,
        @NotBlank @Size(max = 120) String nombre,
        @NotNull @DecimalMin("0") @Digits(integer = 3, fraction = 6) BigDecimal tasa,
        @NotNull Boolean incluidoEnPrecio,
        @NotNull LocalDate vigenteDesde,
        LocalDate vigenteHasta) {
}
