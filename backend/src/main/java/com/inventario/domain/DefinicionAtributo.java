package com.inventario.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record DefinicionAtributo(
        UUID id,
        UUID tipoProductoId,
        String nombre,
        String tipoDato,
        boolean obligatorio,
        JsonNode opciones) {
}
