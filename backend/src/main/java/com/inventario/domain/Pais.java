package com.inventario.domain;

import java.util.UUID;

public record Pais(
        UUID id,
        String codigoIso,
        String nombre,
        UUID monedaLocalId,
        UUID idiomaPredeterminadoId,
        String zonaHoraria,
        boolean habilitado) {
}
