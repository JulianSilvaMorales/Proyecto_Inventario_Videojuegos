package com.inventario.dto;

import java.util.List;
import java.util.UUID;

public record PaisVenta(
        UUID paisId,
        String paisCodigo,
        String paisNombre,
        UUID monedaId,
        String monedaCodigo,
        int monedaDecimales,
        List<String> mediosPago) {
}
