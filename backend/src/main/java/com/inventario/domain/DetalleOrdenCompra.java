package com.inventario.domain;

import java.util.List;

public record DetalleOrdenCompra(
        OrdenCompra orden,
        List<OrdenCompraLinea> lineas) {
}
