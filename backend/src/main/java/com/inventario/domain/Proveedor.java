package com.inventario.domain;

import java.util.UUID;

public record Proveedor(
        UUID id,
        String razonSocial,
        UUID paisId,
        String identificacionTributaria,
        String personaContacto,
        String correo,
        String telefono,
        UUID monedaCompraId,
        String condicionesPago,
        boolean activo) {
}
