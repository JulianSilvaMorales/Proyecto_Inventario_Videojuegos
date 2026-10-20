package com.inventario.domain;

import java.util.List;

public record DetalleProducto(
        Producto producto,
        List<ProductoTraduccion> traducciones,
        List<PrecioProducto> precios) {
}
