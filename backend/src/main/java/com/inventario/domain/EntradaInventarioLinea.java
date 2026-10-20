package com.inventario.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record EntradaInventarioLinea(
        UUID id,
        UUID ordenCompraLineaId,
        UUID productoId,
        String codigoProducto,
        int cantidad,
        BigDecimal costoUnitario,
        BigDecimal costoUnitarioMonedaBase) {
}
