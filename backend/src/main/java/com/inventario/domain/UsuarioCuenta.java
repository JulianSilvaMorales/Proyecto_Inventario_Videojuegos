package com.inventario.domain;

import java.time.Instant;
import java.util.UUID;

public record UsuarioCuenta(
        UUID id,
        String correo,
        String nombre,
        String hashContrasena,
        String idioma,
        int intentosFallidos,
        Instant bloqueadoHastaUtc,
        boolean activo) {
}
