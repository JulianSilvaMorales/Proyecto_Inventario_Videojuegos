package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.domain.ParametroResuelto;
import com.inventario.repository.ParametroRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ParametroServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolvesCountryParameterAndCachesItForConfiguredDuration() throws Exception {
        ParametroRepository repository = Mockito.mock(ParametroRepository.class);
        when(repository.buscarVigente("sesion.inactividad_minutos", "CO"))
                .thenReturn(Optional.of(parameter("sesion.inactividad_minutos", 30, "PAIS", "CO")));
        when(repository.buscarVigente("parametros.cache_minutos", null))
                .thenReturn(Optional.of(parameter("parametros.cache_minutos", 5, "NEGOCIO", null)));
        ParametroService service = new ParametroService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.entero("sesion.inactividad_minutos", "co")).isEqualTo(30);
        assertThat(service.entero("sesion.inactividad_minutos", "CO")).isEqualTo(30);

        verify(repository).buscarVigente("sesion.inactividad_minutos", "CO");
        verify(repository).buscarVigente("parametros.cache_minutos", null);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void expiresCachedParameterAfterConfiguredTime() throws Exception {
        ParametroRepository repository = Mockito.mock(ParametroRepository.class);
        MutableClock clock = new MutableClock(NOW);
        when(repository.buscarVigente("seguridad.intentos_fallidos_max", null))
                .thenReturn(Optional.of(parameter("seguridad.intentos_fallidos_max", 5, "NEGOCIO", null)));
        when(repository.buscarVigente("parametros.cache_minutos", null))
                .thenReturn(Optional.of(parameter("parametros.cache_minutos", 1, "NEGOCIO", null)));
        ParametroService service = new ParametroService(repository, clock);

        assertThat(service.entero("seguridad.intentos_fallidos_max", null)).isEqualTo(5);
        clock.advanceSeconds(60);
        assertThat(service.entero("seguridad.intentos_fallidos_max", null)).isEqualTo(5);

        verify(repository, Mockito.times(2))
                .buscarVigente("seguridad.intentos_fallidos_max", null);
    }

    @Test
    void rejectsMissingEffectiveParameterClearly() {
        ParametroRepository repository = Mockito.mock(ParametroRepository.class);
        when(repository.buscarVigente("desconocido", null)).thenReturn(Optional.empty());
        ParametroService service = new ParametroService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.resolver("desconocido", null))
                .isInstanceOf(ParametroNoEncontradoException.class)
                .hasMessageContaining("desconocido");
    }

    private ParametroResuelto parameter(
            String key,
            int value,
            String scope,
            String country) throws Exception {
        JsonNode jsonValue = objectMapper.readTree(Integer.toString(value));
        return new ParametroResuelto(
                key,
                jsonValue,
                "ENTERO",
                BigDecimal.ONE,
                BigDecimal.valueOf(1440),
                jsonValue,
                scope,
                country,
                LocalDate.of(2026, 1, 1),
                null);
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
