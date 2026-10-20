package com.inventario.domain;

import java.util.UUID;

public record Idioma(
        UUID id,
        String codigoBcp47,
        String nombre,
        boolean habilitado) {
}
