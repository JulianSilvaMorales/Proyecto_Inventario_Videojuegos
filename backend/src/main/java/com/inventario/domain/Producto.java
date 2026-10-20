package com.inventario.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record Producto(
        UUID id,
        String codigoInterno,
        UUID tipoProductoId,
        UUID categoriaId,
        UUID subcategoriaId,
        JsonNode atributos,
        boolean activo,
        Instant creadoEnUtc,
        Instant actualizadoEnUtc) {
}
