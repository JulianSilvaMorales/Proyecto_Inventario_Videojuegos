package com.inventario.dto;

import com.inventario.domain.Venta;
import com.inventario.domain.VentaLinea;
import java.util.List;

public record RespuestaVenta(
        Venta venta,
        List<VentaLinea> lineas,
        boolean reciboSimulado) {

    public RespuestaVenta {
        lineas = List.copyOf(lineas);
    }
}
