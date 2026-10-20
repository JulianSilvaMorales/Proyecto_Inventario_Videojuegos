package com.inventario.service;

import com.inventario.domain.IdentidadAutenticada;
import com.inventario.domain.UsuarioCuenta;
import com.inventario.repository.UsuarioRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticacionService {

    private static final String GENERIC_CREDENTIALS_ERROR = "Correo o contraseña incorrectos.";

    private final UsuarioRepository usuarioRepository;
    private final ParametroService parametroService;
    private final PasswordEncoder passwordEncoder;
    private final SesionTracker sesionTracker;
    private final JwtService jwtService;
    private final String dummyPasswordHash;

    public AutenticacionService(
            UsuarioRepository usuarioRepository,
            ParametroService parametroService,
            PasswordEncoder passwordEncoder,
            SesionTracker sesionTracker,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.parametroService = parametroService;
        this.passwordEncoder = passwordEncoder;
        this.sesionTracker = sesionTracker;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public ResultadoAutenticacion iniciarSesion(String correo, String contrasena) {
        Instant ahora = Instant.now();
        UsuarioCuenta account = usuarioRepository
                .buscarPorCorreoParaAutenticar(correo.trim().toLowerCase())
                .orElse(null);
        boolean passwordMatches = passwordEncoder.matches(
                contrasena,
                account == null ? dummyPasswordHash : account.hashContrasena());

        int maximumAttempts = parametroService.entero("seguridad.intentos_fallidos_max", null);
        boolean locked = account != null
                && account.bloqueadoHastaUtc() != null
                && (account.intentosFallidos() >= maximumAttempts
                        || account.bloqueadoHastaUtc().isAfter(ahora));
        if (account == null || !account.activo() || locked || !passwordMatches) {
            if (account != null && account.activo() && !locked && !passwordMatches) {
                int attempts = Math.min(account.intentosFallidos() + 1, maximumAttempts);
                Timestamp blockedAt = attempts >= maximumAttempts ? Timestamp.from(ahora) : null;
                usuarioRepository.registrarIntentoFallido(account.id(), attempts, blockedAt);
            }
            throw new BadCredentialsException(GENERIC_CREDENTIALS_ERROR);
        }

        usuarioRepository.reiniciarIntentosFallidos(account.id());
        IdentidadAutenticada identity = usuarioRepository.cargarIdentidad(account.id());
        Duration idleTimeout = Duration.ofMinutes(
                parametroService.entero("sesion.inactividad_minutos", null));
        UUID sessionId = sesionTracker.crear(ahora);
        TokenEmitido token = jwtService.emitir(identity, sessionId, idleTimeout, ahora);
        return new ResultadoAutenticacion(token.valor(), token.venceEnUtc(), identity);
    }

    public void desbloquearUsuario(UUID usuarioId) {
        if (!usuarioRepository.desbloquear(usuarioId)) {
            throw new UsuarioNoEncontradoException(usuarioId);
        }
    }
}
