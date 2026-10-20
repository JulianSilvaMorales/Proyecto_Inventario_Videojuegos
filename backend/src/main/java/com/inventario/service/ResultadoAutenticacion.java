package com.inventario.service;

import com.inventario.domain.IdentidadAutenticada;
import java.time.Instant;

public record ResultadoAutenticacion(
        String token,
        Instant venceEnUtc,
        IdentidadAutenticada usuario) {
}
