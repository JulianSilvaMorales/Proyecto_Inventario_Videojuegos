package com.inventario.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record SolicitudProducto(
        @NotNull UUID tipoProductoId,
        @NotNull UUID categoriaId,
        @NotNull UUID subcategoriaId,
        @NotNull JsonNode atributos,
        @NotEmpty List<@Valid SolicitudProductoTraduccion> traducciones,
        @NotEmpty List<@Valid SolicitudPrecioProducto> precios) {
}
