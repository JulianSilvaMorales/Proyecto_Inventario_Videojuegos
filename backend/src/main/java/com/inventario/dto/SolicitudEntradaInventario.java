package com.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SolicitudEntradaInventario(
        @NotNull UUID ordenCompraId,
        @NotNull UUID bodegaId,
        @NotBlank @Size(max = 120) String numeroDocumento,
        @NotNull @DecimalMin("0.00000001") @Digits(integer = 11, fraction = 8)
                BigDecimal tipoCambioAplicado,
        @NotEmpty List<@Valid SolicitudLineaEntradaInventario> lineas) {
}
