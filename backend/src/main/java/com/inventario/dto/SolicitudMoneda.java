package com.inventario.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SolicitudMoneda(
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String codigoIso,
        @NotBlank @Size(max = 120) String nombre,
        @NotBlank @Size(max = 12) String simbolo,
        @NotNull @Min(0) @Max(4) Integer decimales,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")
        @Size(max = 40) String formato,
        @NotBlank @Size(max = 40) String reglaRedondeo) {
}
