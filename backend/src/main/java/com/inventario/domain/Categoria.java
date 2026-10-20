package com.inventario.domain;

import java.util.UUID;

public record Categoria(
        UUID id,
        String codigo,
        String nombre,
        UUID padreId,
        boolean activo) {
}
