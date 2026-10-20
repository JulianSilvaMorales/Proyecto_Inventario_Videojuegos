package com.inventario.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrdenCompra(
        UUID id,
        String numero,
        UUID proveedorId,
        String proveedorNombre,
        UUID paisId,
        UUID monedaId,
        BigDecimal tipoCambio,
        String estado,
        UUID creadaPorId,
        OffsetDateTime creadaEnUtc) {
}
