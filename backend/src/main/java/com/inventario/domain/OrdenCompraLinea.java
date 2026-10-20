package com.inventario.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record OrdenCompraLinea(
        UUID id,
        UUID productoId,
        String codigoProducto,
        String nombreProducto,
        int cantidadPedida,
        int cantidadRecibida,
        BigDecimal costoUnitario) {
}
