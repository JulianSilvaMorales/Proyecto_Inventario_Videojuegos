package com.inventario.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CalculoImpuesto(
        UUID impuestoId,
        String nombreImpuesto,
        BigDecimal tasa,
        boolean incluidoEnPrecio,
        BigDecimal precioInformado,
        BigDecimal baseGravable,
        BigDecimal importeImpuesto,
        BigDecimal total,
        String moneda,
        LocalDate fechaAplicada) {
}
