package com.inventario.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AjusteInventario(
        UUID id,
        UUID productoId,
        String codigoProducto,
        UUID bodegaId,
        String bodegaNombre,
        String tipo,
        int cantidadSistema,
        int cantidadContada,
        int diferencia,
        String motivo,
        String estado,
        UUID solicitadoPorId,
        UUID aprobadoPorId,
        OffsetDateTime solicitadoEnUtc,
        OffsetDateTime aprobadoEnUtc,
        OffsetDateTime aplicadoEnUtc) {
}
