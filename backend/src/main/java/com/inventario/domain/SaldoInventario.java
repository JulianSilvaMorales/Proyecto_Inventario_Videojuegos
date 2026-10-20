package com.inventario.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SaldoInventario(
        UUID productoId,
        String codigoProducto,
        String nombreProducto,
        UUID bodegaId,
        String bodegaCodigo,
        String bodegaNombre,
        int cantidad,
        OffsetDateTime actualizadoEnUtc) {
}
