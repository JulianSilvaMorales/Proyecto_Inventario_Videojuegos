package com.inventario.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SolicitudDefinicionAtributo(
        @NotNull UUID tipoProductoId,
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Pattern(regexp = "TEXTO|ENTERO|DECIMAL|BOOLEANO|JSON") String tipoDato,
        @NotNull Boolean obligatorio,
        JsonNode opciones) {
}
