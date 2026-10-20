package com.inventario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SolicitudCliente(
        @NotNull UUID paisId,
        @NotBlank @Pattern(regexp = "INFRECUENTE|FRECUENTE") String clasificacion,
        @NotBlank @Size(max = 160) String nombre,
        @Size(max = 160) String apellidos,
        @Size(max = 40) String tipoDocumento,
        @Size(max = 80) String documento,
        @Size(max = 20) String codigoPostal,
        @Size(max = 300) String direccion,
        @Size(max = 40) String telefono,
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank @Size(max = 160) String baseLegal,
        @NotNull Boolean consentimientoRegistrado,
        @NotNull Boolean activo) {
}
