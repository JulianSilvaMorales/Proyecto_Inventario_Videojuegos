package com.inventario.service;

import com.inventario.domain.IdentidadAutenticada;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    public TokenEmitido emitir(
            IdentidadAutenticada identidad,
            UUID sesionId,
            Duration vigencia,
            Instant ahora) {
        Instant venceEn = ahora.plus(vigencia);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("inventario")
                .issuedAt(ahora)
                .expiresAt(venceEn)
                .subject(identidad.correo())
                .id(sesionId.toString())
                .claim("usuario_id", identidad.usuarioId().toString())
                .claim("nombre", identidad.nombre())
                .claim("idioma", identidad.idioma())
                .claim(
                        "roles",
                        identidad.roles().stream().sorted().toList())
                .claim(
                        "permisos",
                        identidad.permisos().stream().sorted(Comparator.naturalOrder()).toList())
                .claim(
                        "paises",
                        identidad.paises().stream().sorted(Comparator.naturalOrder()).toList())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();
        return new TokenEmitido(token, venceEn);
    }

    public Jwt decodificar(String valor) {
        return decoder.decode(valor);
    }
}
