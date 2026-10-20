package com.inventario.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ConversionMoneda(
        UUID tipoCambioId,
        BigDecimal importeOrigen,
        String monedaOrigen,
        BigDecimal tasaAplicada,
        BigDecimal importeDestino,
        String monedaDestino,
        Instant fechaTipoCambioUtc) {
}
