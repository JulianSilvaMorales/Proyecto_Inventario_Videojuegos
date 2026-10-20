package com.inventario.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Venta(
        UUID id,
        String numero,
        UUID clienteId,
        UUID paisId,
        String paisCodigo,
        UUID monedaId,
        String monedaCodigo,
        BigDecimal tipoCambioAplicado,
        BigDecimal subtotal,
        BigDecimal totalImpuestos,
        BigDecimal total,
        BigDecimal totalMonedaBase,
        String estado,
        String estadoPago,
        String medioPago,
        UUID registradaPorId,
        OffsetDateTime creadaEnUtc,
        OffsetDateTime pagadaEnUtc,
        OffsetDateTime confirmadaEnUtc,
        OffsetDateTime anuladaEnUtc) {
}
