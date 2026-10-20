package com.inventario.domain;

import java.util.UUID;

public record Moneda(
        UUID id,
        String codigoIso,
        String nombre,
        String simbolo,
        int decimales,
        String formato,
        String reglaRedondeo) {
}
