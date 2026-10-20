package com.inventario.domain;

import java.util.UUID;

public record ProductoTraduccion(
        UUID id,
        UUID productoId,
        UUID idiomaId,
        String idiomaCodigo,
        String nombre,
        String descripcion) {
}
