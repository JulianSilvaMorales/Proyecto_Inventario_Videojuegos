package com.inventario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SolicitudProveedor(
        @NotBlank @Size(min = 2, max = 200) String razonSocial,
        @NotNull UUID paisId,
        @Size(max = 80) String identificacionTributaria,
        @Size(max = 160) String personaContacto,
        @Email @Size(max = 254) String correo,
        @Size(max = 40) String telefono,
        @NotNull UUID monedaCompraId,
        @Size(max = 500) String condicionesPago,
        @NotNull Boolean activo) {
}
