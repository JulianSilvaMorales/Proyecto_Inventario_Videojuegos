package com.inventario.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SolicitudActualizacionProducto(
        @NotNull UUID tipoProductoId,
        @NotNull UUID categoriaId,
        @NotNull UUID subcategoriaId,
        @NotNull JsonNode atributos) {
}
