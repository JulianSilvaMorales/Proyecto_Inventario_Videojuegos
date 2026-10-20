package com.inventario.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UsuarioAutenticado(
        UUID usuarioId,
        String correo,
        String nombre,
        String idioma,
        List<String> roles,
        Set<String> permisos,
        Set<String> paises,
        UUID sesionId) {
}
