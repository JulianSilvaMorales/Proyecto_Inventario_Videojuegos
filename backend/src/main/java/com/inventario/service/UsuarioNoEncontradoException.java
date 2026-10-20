package com.inventario.service;

import java.util.UUID;

public class UsuarioNoEncontradoException extends RuntimeException {

    public UsuarioNoEncontradoException(UUID usuarioId) {
        super("No se encontró el usuario " + usuarioId + ".");
    }
}
