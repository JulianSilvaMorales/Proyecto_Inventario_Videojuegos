package com.inventario.service;

import com.inventario.domain.ParametroResuelto;
import com.inventario.repository.ParametroRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ParametroService {

    private static final String CACHE_MINUTES_KEY = "parametros.cache_minutos";

    private final ParametroRepository repository;
    private final Clock clock;
    private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

    public ParametroService(ParametroRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ParametroResuelto resolver(String clave, String paisCodigo) {
        String normalizedCountry = paisCodigo == null || paisCodigo.isBlank()
                ? null
                : paisCodigo.trim().toUpperCase();
        if (CACHE_MINUTES_KEY.equals(clave)) {
            return buscarVigente(clave, normalizedCountry);
        }

        CacheKey cacheKey = new CacheKey(clave, normalizedCountry);
        Instant now = clock.instant();
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null
                && cached.expiresAt().isAfter(now)
                && esVigenteHoy(cached.value(), now)) {
            return cached.value();
        }
        if (cached != null) {
            cache.remove(cacheKey, cached);
        }

        ParametroResuelto value = buscarVigente(clave, normalizedCountry);
        int cacheMinutes = resolverCacheMinutos();
        Instant expiresAt = now.plus(Duration.ofMinutes(cacheMinutes));
        if (value.vigenteHasta() != null) {
            Instant endOfValidity = value.vigenteHasta()
                    .plusDays(1)
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC);
            if (endOfValidity.isBefore(expiresAt)) {
                expiresAt = endOfValidity;
            }
        }
        cache.put(cacheKey, new CacheEntry(value, expiresAt));
        return value;
    }

    public int entero(String clave, String paisCodigo) {
        ParametroResuelto parameter = resolver(clave, paisCodigo);
        int value = parameter.entero();
        validarRango(parameter, value);
        return value;
    }

    public void invalidarTodo() {
        cache.clear();
    }

    private int resolverCacheMinutos() {
        ParametroResuelto parameter = buscarVigente(CACHE_MINUTES_KEY, null);
        int minutes = parameter.entero();
        if (minutes < 1) {
            throw new IllegalStateException(
                    "El parámetro 'parametros.cache_minutos' debe ser mayor que cero.");
        }
        validarRango(parameter, minutes);
        return minutes;
    }

    private void validarRango(ParametroResuelto parameter, int value) {
        if (parameter.minimo() != null
                && parameter.minimo().compareTo(BigDecimal.valueOf(value)) > 0) {
            throw new IllegalStateException(
                    "El parámetro '" + parameter.clave() + "' está por debajo de su mínimo permitido.");
        }
        if (parameter.maximo() != null
                && parameter.maximo().compareTo(BigDecimal.valueOf(value)) < 0) {
            throw new IllegalStateException(
                    "El parámetro '" + parameter.clave() + "' supera su máximo permitido.");
        }
    }

    private ParametroResuelto buscarVigente(String clave, String paisCodigo) {
        return repository.buscarVigente(clave, paisCodigo)
                .orElseThrow(() -> new ParametroNoEncontradoException(clave, paisCodigo));
    }

    private boolean esVigenteHoy(ParametroResuelto value, Instant now) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        return !value.vigenteDesde().isAfter(today)
                && (value.vigenteHasta() == null || !value.vigenteHasta().isBefore(today));
    }

    private record CacheKey(String clave, String paisCodigo) {
    }

    private record CacheEntry(ParametroResuelto value, Instant expiresAt) {
    }
}
