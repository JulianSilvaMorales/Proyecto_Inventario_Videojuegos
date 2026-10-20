package com.inventario.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record Impuesto(
        UUID id,
        UUID paisId,
        UUID categoriaId,
        String nombre,
        BigDecimal tasa,
        boolean incluidoEnPrecio,
        LocalDate vigenteDesde,
        LocalDate vigenteHasta) {
}
