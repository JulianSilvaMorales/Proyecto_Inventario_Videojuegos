package com.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SolicitudPais(
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String codigoIso,
        @NotBlank @Size(max = 160) String nombre,
        UUID monedaLocalId,
        UUID idiomaPredeterminadoId,
        @Size(max = 80) String zonaHoraria,
        @NotNull Boolean habilitado) {
}
