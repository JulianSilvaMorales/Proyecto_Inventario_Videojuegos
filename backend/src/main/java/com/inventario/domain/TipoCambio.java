package com.inventario.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TipoCambio(
        UUID id,
        UUID monedaOrigenId,
        String monedaOrigenCodigo,
        UUID monedaDestinoId,
        String monedaDestinoCodigo,
        BigDecimal tasa,
        Instant vigenteDesdeUtc,
        String origen,
        UUID actualizadoPorId) {
}
