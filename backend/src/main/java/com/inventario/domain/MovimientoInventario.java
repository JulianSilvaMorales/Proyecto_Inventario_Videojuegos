package com.inventario.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MovimientoInventario(
        UUID id,
        UUID productoId,
        String codigoProducto,
        UUID bodegaId,
        String bodegaNombre,
        String tipo,
        int cantidad,
        String referenciaTipo,
        UUID referenciaId,
        UUID usuarioId,
        OffsetDateTime fechaUtc,
        String observacion) {
}
