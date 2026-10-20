package com.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitudConfirmacionVenta(
        @NotBlank @Size(max = 40) String medioPago) {
}
