package com.inventario.domain;

import java.util.UUID;

public record TipoProducto(UUID id, String codigo, String nombre, boolean activo) {
}
