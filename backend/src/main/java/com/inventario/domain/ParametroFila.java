package com.inventario.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ParametroFila(
        UUID id,
        String clave,
        JsonNode valor,
        String tipo,
        BigDecimal minimo,
        BigDecimal maximo,
        JsonNode valorPorDefecto,
        String ambito,
        UUID paisId,
        String paisCodigo,
        LocalDate vigenteDesde,
        LocalDate vigenteHasta,
        UUID actualizadoPorId,
        OffsetDateTime actualizadoEnUtc) {
}
