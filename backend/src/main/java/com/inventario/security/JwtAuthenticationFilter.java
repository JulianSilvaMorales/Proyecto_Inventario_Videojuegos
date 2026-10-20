package com.inventario.security;

import com.inventario.domain.IdentidadAutenticada;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.service.JwtService;
import com.inventario.service.ParametroService;
import com.inventario.service.SesionTracker;
import com.inventario.service.TokenEmitido;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String REFRESHED_TOKEN_HEADER = "X-Auth-Token";

    private final JwtService jwtService;
    private final SesionTracker sesionTracker;
    private final ParametroService parametroService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            SesionTracker sesionTracker,
            ParametroService parametroService) {
        this.jwtService = jwtService;
        this.sesionTracker = sesionTracker;
        this.parametroService = parametroService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/api/v1/auth/login")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, java.io.IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Jwt token = jwtService.decodificar(authorization.substring(7).trim());
            UUID sessionId = UUID.fromString(token.getId());
            Duration idleTimeout = Duration.ofMinutes(
                    parametroService.entero("sesion.inactividad_minutos", null));
            Instant now = Instant.now();
            if (!sesionTracker.registrarActividad(sessionId, now, idleTimeout)) {
                filterChain.doFilter(request, response);
                return;
            }

            UsuarioAutenticado user = toUser(token, sessionId);
            List<SimpleGrantedAuthority> authorities = user.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toCollection(java.util.ArrayList::new));
            user.permisos().stream()
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            response.setHeader("Cache-Control", "no-store");

            if (!request.getServletPath().equals("/api/v1/auth/logout")) {
                IdentidadAutenticada identity = new IdentidadAutenticada(
                        user.usuarioId(),
                        user.correo(),
                        user.nombre(),
                        user.idioma(),
                        user.roles(),
                        user.permisos(),
                        user.paises());
                TokenEmitido refreshedToken = jwtService.emitir(
                        identity,
                        sessionId,
                        idleTimeout,
                        now);
                response.setHeader(REFRESHED_TOKEN_HEADER, refreshedToken.valor());
            }
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    private UsuarioAutenticado toUser(Jwt token, UUID sessionId) {
        UUID userId = UUID.fromString(token.getClaimAsString("usuario_id"));
        return new UsuarioAutenticado(
                userId,
                token.getSubject(),
                token.getClaimAsString("nombre"),
                token.getClaimAsString("idioma"),
                stringList(token.getClaim("roles")),
                Set.copyOf(stringList(token.getClaim("permisos"))),
                Set.copyOf(stringList(token.getClaim("paises"))),
                sessionId);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
