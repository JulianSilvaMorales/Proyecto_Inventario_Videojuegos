package com.inventario.service;

public class ParametroNoEncontradoException extends RuntimeException {

    public ParametroNoEncontradoException(String clave, String paisCodigo) {
        super(paisCodigo == null
                ? "No existe un parámetro vigente con clave '" + clave + "'."
                : "No existe un parámetro vigente con clave '" + clave
                        + "' para el país '" + paisCodigo + "'.");
    }
}
