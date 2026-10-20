package com.inventario.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ParametroResuelto(
        String clave,
        JsonNode valor,
        String tipo,
        BigDecimal minimo,
        BigDecimal maximo,
        JsonNode valorPorDefecto,
        String ambito,
        String paisCodigo,
        LocalDate vigenteDesde,
        LocalDate vigenteHasta) {

    public int entero() {
        if (!"ENTERO".equals(tipo)
                || valor == null
                || !valor.isIntegralNumber()
                || !valor.canConvertToInt()) {
            throw new IllegalStateException("El parámetro '" + clave + "' no contiene un entero.");
        }
        return valor.intValue();
    }
}
