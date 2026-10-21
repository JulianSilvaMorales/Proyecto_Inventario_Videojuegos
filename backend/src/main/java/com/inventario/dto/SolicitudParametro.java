package com.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SolicitudParametro(
        @NotBlank @Size(max = 100) String clave,
        @NotNull Object valor,
        @NotBlank @Size(max = 20) String tipo,
        BigDecimal minimo,
        BigDecimal maximo,
        @NotNull @Size(max = 10) String ambito,
        UUID paisId,
        @NotNull LocalDate vigenteDesde,
        LocalDate vigenteHasta) {
}
