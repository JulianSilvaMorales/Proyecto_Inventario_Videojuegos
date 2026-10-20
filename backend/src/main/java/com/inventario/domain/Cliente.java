package com.inventario.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Cliente(
        UUID id,
        UUID paisId,
        String clasificacion,
        String nombre,
        String apellidos,
        String tipoDocumento,
        String documento,
        String codigoPostal,
        String direccion,
        String telefono,
        String correo,
        String baseLegal,
        boolean consentimientoRegistrado,
        OffsetDateTime consentimientoEnUtc,
        OffsetDateTime ultimoContactoEnUtc,
        boolean activo) {
}
