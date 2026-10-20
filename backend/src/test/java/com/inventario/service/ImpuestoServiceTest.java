package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.inventario.domain.Impuesto;
import com.inventario.domain.Moneda;
import com.inventario.repository.ConfiguracionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImpuestoServiceTest {

    @Mock
    private ConfiguracionRepository repository;

    @InjectMocks
    private ImpuestoService service;

    private UUID countryId;
    private UUID currencyId;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        countryId = UUID.randomUUID();
        currencyId = UUID.randomUUID();
        date = LocalDate.of(2026, 5, 1);
    }

    @Test
    void calcularImpuestoDesglosaPrecioQueYaLoIncluye() {
        configurarMoneda();
        when(repository.buscarImpuestoVigente(countryId, null, date))
                .thenReturn(Optional.of(impuesto(true)));

        var result = service.calcular(
                countryId,
                null,
                currencyId,
                new BigDecimal("119.00"),
                date);

        assertThat(result.baseGravable()).isEqualByComparingTo("100.00");
        assertThat(result.importeImpuesto()).isEqualByComparingTo("19.00");
        assertThat(result.total()).isEqualByComparingTo("119.00");
    }

    @Test
    void calcularImpuestoAgregaTarifaCuandoElPrecioNoLoIncluye() {
        configurarMoneda();
        when(repository.buscarImpuestoVigente(countryId, null, date))
                .thenReturn(Optional.of(impuesto(false)));

        var result = service.calcular(
                countryId,
                null,
                currencyId,
                new BigDecimal("100.00"),
                date);

        assertThat(result.baseGravable()).isEqualByComparingTo("100.00");
        assertThat(result.importeImpuesto()).isEqualByComparingTo("19.00");
        assertThat(result.total()).isEqualByComparingTo("119.00");
    }

    @Test
    void fallaCuandoNoExisteImpuestoVigente() {
        when(repository.buscarImpuestoVigente(countryId, null, date))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calcular(
                countryId,
                null,
                currencyId,
                new BigDecimal("100.00"),
                date))
                .isInstanceOf(ErrorAplicacionException.class);
    }

    private Impuesto impuesto(boolean included) {
        return new Impuesto(
                UUID.randomUUID(),
                countryId,
                null,
                "IVA",
                new BigDecimal("0.19"),
                included,
                LocalDate.of(2026, 1, 1),
                null);
    }

    private void configurarMoneda() {
        when(repository.buscarMoneda(currencyId))
                .thenReturn(Optional.of(new Moneda(
                        currencyId,
                        "COP",
                        "Peso colombiano",
                        "$",
                        2,
                        "es-CO",
                        "HALF_UP")));
    }
}
