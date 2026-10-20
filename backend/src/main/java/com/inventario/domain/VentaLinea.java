package com.inventario.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record VentaLinea(
        UUID id,
        UUID ventaId,
        UUID productoId,
        String codigoProducto,
        String nombreProducto,
        UUID bodegaId,
        String bodegaNombre,
        int cantidad,
        BigDecimal precioUnitario,
        UUID impuestoId,
        String nombreImpuestoAplicado,
        BigDecimal tasaImpuestoAplicada,
        BigDecimal importeImpuesto,
        BigDecimal subtotalLinea,
        BigDecimal totalLinea) {
}
