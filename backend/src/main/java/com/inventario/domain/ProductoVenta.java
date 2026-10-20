package com.inventario.domain;

import java.util.UUID;

public record ProductoVenta(
        UUID id,
        String codigoInterno,
        UUID categoriaId,
        boolean activo) {
}
