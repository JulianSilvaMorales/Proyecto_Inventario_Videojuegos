package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SesionTrackerTest {

    @Test
    void expiresSessionAfterInactivityAndSupportsExplicitLogout() {
        SesionTracker tracker = new SesionTracker(mock(ParametroService.class));
        Instant created = Instant.parse("2026-10-20T12:00:00Z");
        UUID sessionId = tracker.crear(created);

        assertThat(tracker.registrarActividad(
                        sessionId,
                        created.plusSeconds(59),
                        Duration.ofMinutes(1)))
                .isTrue();
        assertThat(tracker.registrarActividad(
                        sessionId,
                        created.plusSeconds(118),
                        Duration.ofMinutes(1)))
                .isTrue();
        assertThat(tracker.registrarActividad(
                        sessionId,
                        created.plusSeconds(178),
                        Duration.ofMinutes(1)))
                .isFalse();
        assertThat(tracker.registrarActividad(
                        sessionId,
                        created.plusSeconds(179),
                        Duration.ofMinutes(1)))
                .isFalse();
    }

    @Test
    void revokesSessionOnLogout() {
        SesionTracker tracker = new SesionTracker(mock(ParametroService.class));
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        UUID sessionId = tracker.crear(now);

        tracker.cerrar(sessionId);

        assertThat(tracker.registrarActividad(
                        sessionId,
                        now.plusSeconds(1),
                        Duration.ofMinutes(1)))
                .isFalse();
    }
}
