package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.inventario.domain.IdentidadAutenticada;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtServiceTest {

    @Test
    void signsJwtWithIdentityPermissionsAndConfiguredExpiry() {
        SecretKey key = new SecretKeySpec(new byte[32], "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtService service = new JwtService(encoder, decoder);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        IdentidadAutenticada identity = new IdentidadAutenticada(
                userId,
                "admin@example.test",
                "Administradora",
                "es",
                List.of("ADMINISTRADOR"),
                Set.of("USUARIOS_GESTIONAR"),
                Set.of("CO"));

        TokenEmitido issued = service.emitir(
                identity,
                sessionId,
                Duration.ofMinutes(60),
                now);
        var decoded = service.decodificar(issued.valor());

        assertThat(decoded.getSubject()).isEqualTo("admin@example.test");
        assertThat(decoded.getId()).isEqualTo(sessionId.toString());
        assertThat(decoded.getClaimAsString("usuario_id")).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMINISTRADOR");
        assertThat(decoded.getClaimAsStringList("permisos"))
                .containsExactly("USUARIOS_GESTIONAR");
        assertThat(decoded.getClaimAsStringList("paises")).containsExactly("CO");
        assertThat(issued.venceEnUtc()).isEqualTo(now.plus(Duration.ofMinutes(60)));
    }
}
