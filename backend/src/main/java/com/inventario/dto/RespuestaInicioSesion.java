package com.inventario.dto;

import com.inventario.domain.IdentidadAutenticada;
import java.time.Instant;

public record RespuestaInicioSesion(
        String tipoToken,
        String token,
        Instant venceEnUtc,
        IdentidadAutenticada usuario) {
}
