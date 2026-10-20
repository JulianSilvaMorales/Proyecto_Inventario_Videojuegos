package com.inventario.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SesionTracker {

    private final ConcurrentMap<UUID, Instant> ultimoAcceso = new ConcurrentHashMap<>();
    private final ParametroService parametroService;

    public SesionTracker(ParametroService parametroService) {
        this.parametroService = parametroService;
    }

    public UUID crear(Instant ahora) {
        UUID sesionId = UUID.randomUUID();
        ultimoAcceso.put(sesionId, ahora);
        return sesionId;
    }

    public boolean registrarActividad(UUID sesionId, Instant ahora, Duration inactividadMaxima) {
        AtomicBoolean activa = new AtomicBoolean();
        ultimoAcceso.computeIfPresent(sesionId, (id, ultimo) -> {
            if (!ultimo.plus(inactividadMaxima).isAfter(ahora)) {
                return null;
            }
            activa.set(true);
            return ahora;
        });
        return activa.get();
    }

    public void cerrar(UUID sesionId) {
        ultimoAcceso.remove(sesionId);
    }

    @Scheduled(fixedDelay = 60_000)
    void limpiarSesionesInactivas() {
        Duration inactividadMaxima = Duration.ofMinutes(
                parametroService.entero("sesion.inactividad_minutos", null));
        Instant ahora = Instant.now();
        ultimoAcceso.entrySet().removeIf(
                entry -> !entry.getValue().plus(inactividadMaxima).isAfter(ahora));
    }
}
