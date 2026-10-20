package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inventario.domain.Moneda;
import com.inventario.domain.TipoCambio;
import com.inventario.repository.ConfiguracionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversionMonedaServiceTest {

    @Mock
    private ConfiguracionRepository repository;

    @InjectMocks
    private ConversionMonedaService service;

    private UUID copId;
    private UUID usdId;
    private Instant effectiveAt;

    @BeforeEach
    void setUp() {
        copId = UUID.randomUUID();
        usdId = UUID.randomUUID();
        effectiveAt = Instant.parse("2026-02-01T00:00:00Z");
    }

    @Test
    void convertirUsaLaTasaDirectaVigenteYRedondeaSegunMonedaDestino() {
        when(repository.buscarMoneda(copId))
                .thenReturn(Optional.of(moneda(copId, "COP", 2)));
        when(repository.buscarMoneda(usdId))
                .thenReturn(Optional.of(moneda(usdId, "USD", 2)));
        UUID rateId = UUID.randomUUID();
        when(repository.buscarTipoCambioVigente(copId, usdId, effectiveAt))
                .thenReturn(Optional.of(tipoCambio(
                        rateId,
                        copId,
                        "COP",
                        usdId,
                        "USD",
                        "0.00400000")));

        var result = service.convertir(
                copId,
                usdId,
                new BigDecimal("1000.00"),
                effectiveAt);

        assertThat(result.tipoCambioId()).isEqualTo(rateId);
        assertThat(result.importeDestino()).isEqualByComparingTo("4.00");
        assertThat(result.tasaAplicada()).isEqualByComparingTo("0.00400000");
        assertThat(result.monedaDestino()).isEqualTo("USD");
    }

    @Test
    void convertirInvierteLaTasaCuandoNoHayTasaDirecta() {
        when(repository.buscarMoneda(copId))
                .thenReturn(Optional.of(moneda(copId, "COP", 2)));
        when(repository.buscarMoneda(usdId))
                .thenReturn(Optional.of(moneda(usdId, "USD", 2)));
        when(repository.buscarTipoCambioVigente(copId, usdId, effectiveAt))
                .thenReturn(Optional.empty());
        when(repository.buscarTipoCambioVigente(usdId, copId, effectiveAt))
                .thenReturn(Optional.of(tipoCambio(
                        UUID.randomUUID(),
                        usdId,
                        "USD",
                        copId,
                        "COP",
                        "4000.00000000")));

        var result = service.convertir(
                copId,
                usdId,
                new BigDecimal("10000.00"),
                effectiveAt);

        assertThat(result.importeDestino()).isEqualByComparingTo("2.50");
        assertThat(result.tasaAplicada()).isEqualByComparingTo("0.0002500000000000");
    }

    @Test
    void monedaIgualNoRequiereTasaDeCambio() {
        when(repository.buscarMoneda(copId))
                .thenReturn(Optional.of(moneda(copId, "COP", 2)));
        var result = service.convertir(
                copId,
                copId,
                new BigDecimal("14.256"),
                effectiveAt);

        assertThat(result.tipoCambioId()).isNull();
        assertThat(result.importeOrigen()).isEqualByComparingTo("14.26");
        assertThat(result.importeDestino()).isEqualByComparingTo("14.26");
        verify(repository, org.mockito.Mockito.never())
                .buscarTipoCambioVigente(copId, copId, effectiveAt);
    }

    @Test
    void convertirFallaSiNoHayTasaDirectaNiInversa() {
        when(repository.buscarMoneda(copId))
                .thenReturn(Optional.of(moneda(copId, "COP", 2)));
        when(repository.buscarMoneda(usdId))
                .thenReturn(Optional.of(moneda(usdId, "USD", 2)));
        when(repository.buscarTipoCambioVigente(copId, usdId, effectiveAt))
                .thenReturn(Optional.empty());
        when(repository.buscarTipoCambioVigente(usdId, copId, effectiveAt))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.convertir(
                copId,
                usdId,
                BigDecimal.TEN,
                effectiveAt))
                .isInstanceOf(ErrorAplicacionException.class);
    }

    private Moneda moneda(UUID id, String code, int decimals) {
        return new Moneda(id, code, code, "$", decimals, "es-CO", "HALF_UP");
    }

    private TipoCambio tipoCambio(
            UUID id,
            UUID sourceId,
            String sourceCode,
            UUID targetId,
            String targetCode,
            String rate) {
        return new TipoCambio(
                id,
                sourceId,
                sourceCode,
                targetId,
                targetCode,
                new BigDecimal(rate),
                Instant.parse("2026-01-01T00:00:00Z"),
                "MANUAL",
                UUID.randomUUID());
    }
}
