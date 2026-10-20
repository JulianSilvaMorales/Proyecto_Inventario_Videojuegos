package com.inventario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SolicitudInicioSesion(
        @NotBlank @Email String correo,
        @NotBlank String contrasena) {
}
