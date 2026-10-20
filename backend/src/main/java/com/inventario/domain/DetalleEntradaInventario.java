package com.inventario.domain;

import java.util.List;

public record DetalleEntradaInventario(
        EntradaInventario entrada,
        List<EntradaInventarioLinea> lineas) {
}
