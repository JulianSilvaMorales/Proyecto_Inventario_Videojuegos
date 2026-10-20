package com.inventario.domain;

import java.util.UUID;

public record Bodega(
        UUID id,
        String codigo,
        String nombre,
        UUID categoriaId,
        boolean activo) {
}
