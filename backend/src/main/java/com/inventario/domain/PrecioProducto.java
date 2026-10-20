package com.inventario.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PrecioProducto(
        UUID id,
        UUID productoId,
        UUID monedaId,
        String monedaCodigo,
        BigDecimal importe,
        LocalDate vigenteDesde,
        LocalDate vigenteHasta) {
}
