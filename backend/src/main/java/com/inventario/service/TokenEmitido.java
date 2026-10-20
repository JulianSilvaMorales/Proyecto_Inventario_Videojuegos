package com.inventario.service;

import java.time.Instant;

public record TokenEmitido(String valor, Instant venceEnUtc) {
}
