package com.inventario.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record LineaRecepcion(
        UUID id,
        UUID productoId,
        int cantidadPedida,
        int cantidadRecibida,
        BigDecimal costoUnitario) {
}
