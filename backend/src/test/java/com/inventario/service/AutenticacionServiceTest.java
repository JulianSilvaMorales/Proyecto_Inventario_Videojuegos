package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inventario.domain.IdentidadAutenticada;
import com.inventario.domain.UsuarioCuenta;
import com.inventario.repository.UsuarioRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AutenticacionServiceTest {

    private final UsuarioRepository users = mock(UsuarioRepository.class);
    private final ParametroService parameters = mock(ParametroService.class);
    private final SesionTracker sessions = mock(SesionTracker.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final AutenticacionService service = new AutenticacionService(
            users,
            parameters,
            passwordEncoder,
            sessions,
            jwtService);
    private final UUID userId = UUID.randomUUID();
    private final String passwordHash = passwordEncoder.encode("correcta");

    @BeforeEach
    void setUp() {
        when(parameters.entero("seguridad.intentos_fallidos_max", null)).thenReturn(5);
    }

    @Test
    void successfulLoginResetsFailuresAndIssuesTokenWithConfiguredIdleTime() {
        UsuarioCuenta account = account(3, null, true, passwordHash);
        IdentidadAutenticada identity = new IdentidadAutenticada(
                userId,
                "admin@example.test",
                "Administradora",
                "es",
                List.of("ADMINISTRADOR"),
                Set.of("USUARIOS_GESTIONAR"),
                Set.of());
        UUID sessionId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(60));
        when(users.buscarPorCorreoParaAutenticar("admin@example.test"))
                .thenReturn(Optional.of(account));
        when(users.cargarIdentidad(userId)).thenReturn(identity);
        when(parameters.entero("sesion.inactividad_minutos", null)).thenReturn(60);
        when(sessions.crear(any(Instant.class))).thenReturn(sessionId);
        when(jwtService.emitir(eq(identity), eq(sessionId), eq(Duration.ofMinutes(60)), any()))
                .thenReturn(new TokenEmitido("jwt-de-prueba", expiresAt));

        ResultadoAutenticacion result = service.iniciarSesion(
                "ADMIN@example.test",
                "correcta");

        assertThat(result.token()).isEqualTo("jwt-de-prueba");
        assertThat(result.usuario()).isEqualTo(identity);
        verify(users).reiniciarIntentosFallidos(userId);
        verify(sessions).crear(any(Instant.class));
    }

    @Test
    void fifthFailedAttemptPersistsLockUntilAdministratorUnlocks() {
        UsuarioCuenta account = account(4, null, true, passwordHash);
        when(users.buscarPorCorreoParaAutenticar("admin@example.test"))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.iniciarSesion(
                        "admin@example.test",
                        "incorrecta"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Correo o contraseña incorrectos.");

        verify(users).registrarIntentoFallido(
                eq(userId),
                eq(5),
                any(Timestamp.class));
        verify(sessions, never()).crear(any(Instant.class));
    }

    @Test
    void lockedAccountCannotLoginEvenWithCorrectPassword() {
        UsuarioCuenta account = account(5, Instant.now(), true, passwordHash);
        when(users.buscarPorCorreoParaAutenticar("admin@example.test"))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.iniciarSesion(
                        "admin@example.test",
                        "correcta"))
                .isInstanceOf(BadCredentialsException.class);

        verify(users, never()).registrarIntentoFallido(any(), anyInt(), any());
        verify(users, never()).cargarIdentidad(userId);
    }

    @Test
    void disabledUserCannotLogin() {
        UsuarioCuenta account = account(0, null, false, passwordHash);
        when(users.buscarPorCorreoParaAutenticar("admin@example.test"))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.iniciarSesion(
                        "admin@example.test",
                        "correcta"))
                .isInstanceOf(BadCredentialsException.class);

        verify(users, never()).registrarIntentoFallido(any(), anyInt(), any());
    }

    @Test
    void administratorUnlockResetsAccountThroughRepository() {
        when(users.desbloquear(userId)).thenReturn(true);

        service.desbloquearUsuario(userId);

        verify(users).desbloquear(userId);
    }

    @Test
    void unlockReportsMissingUser() {
        when(users.desbloquear(userId)).thenReturn(false);

        assertThatThrownBy(() -> service.desbloquearUsuario(userId))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }

    private UsuarioCuenta account(
            int failedAttempts,
            Instant blockedAt,
            boolean active,
            String hash) {
        return new UsuarioCuenta(
                userId,
                "admin@example.test",
                "Administradora",
                hash,
                "es",
                failedAttempts,
                blockedAt,
                active);
    }
}
