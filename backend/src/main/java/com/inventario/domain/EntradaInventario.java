package com.inventario.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EntradaInventario(
        UUID id,
        UUID ordenCompraId,
        String numeroOrdenCompra,
        UUID bodegaId,
        String numeroDocumento,
        BigDecimal tipoCambioAplicado,
        UUID recibidaPorId,
        OffsetDateTime recibidaEnUtc) {
}
