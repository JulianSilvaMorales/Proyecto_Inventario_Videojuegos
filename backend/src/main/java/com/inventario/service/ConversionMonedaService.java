package com.inventario.service;

import com.inventario.domain.ConversionMoneda;
import com.inventario.domain.Moneda;
import com.inventario.domain.TipoCambio;
import com.inventario.repository.ConfiguracionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversionMonedaService {

    private static final int INVERSE_RATE_SCALE = 16;

    private final ConfiguracionRepository repository;

    public ConversionMonedaService(ConfiguracionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ConversionMoneda convertir(
            UUID monedaOrigenId,
            UUID monedaDestinoId,
            BigDecimal importe,
            Instant fecha) {
        Moneda monedaOrigen = obtenerMoneda(monedaOrigenId);
        Moneda monedaDestino = obtenerMoneda(monedaDestinoId);
        RoundingMode roundingMode = roundingMode(monedaDestino);
        BigDecimal importeOrigen = importe.setScale(
                monedaOrigen.decimales(),
                roundingMode(monedaOrigen));

        if (monedaOrigenId.equals(monedaDestinoId)) {
            return new ConversionMoneda(
                    null,
                    importeOrigen,
                    monedaOrigen.codigoIso(),
                    BigDecimal.ONE,
                    importeOrigen.setScale(monedaDestino.decimales(), roundingMode),
                    monedaDestino.codigoIso(),
                    fecha);
        }

        TipoCambio rate = repository.buscarTipoCambioVigente(
                        monedaOrigenId,
                        monedaDestinoId,
                        fecha)
                .orElse(null);
        BigDecimal appliedRate;
        if (rate != null) {
            appliedRate = rate.tasa();
        } else {
            rate = repository.buscarTipoCambioVigente(
                            monedaDestinoId,
                            monedaOrigenId,
                            fecha)
                    .orElseThrow(() -> new ErrorAplicacionException(
                            HttpStatus.NOT_FOUND,
                            "error.tipo-cambio.missing"));
            if (rate.tasa().signum() <= 0) {
                throw new IllegalStateException(
                        "El tipo de cambio almacenado debe ser mayor que cero.");
            }
            appliedRate = BigDecimal.ONE.divide(
                    rate.tasa(),
                    INVERSE_RATE_SCALE,
                    roundingMode);
        }
        if (rate.tasa().signum() <= 0) {
            throw new IllegalStateException(
                    "El tipo de cambio almacenado debe ser mayor que cero.");
        }

        BigDecimal converted = importeOrigen.multiply(appliedRate)
                .setScale(monedaDestino.decimales(), roundingMode);
        return new ConversionMoneda(
                rate.id(),
                importeOrigen,
                monedaOrigen.codigoIso(),
                appliedRate,
                converted,
                monedaDestino.codigoIso(),
                rate.vigenteDesdeUtc());
    }

    private Moneda obtenerMoneda(UUID id) {
        return repository.buscarMoneda(id)
                .orElseThrow(() -> new ErrorAplicacionException(
                        HttpStatus.NOT_FOUND,
                        "error.moneda.not-found"));
    }

    private RoundingMode roundingMode(Moneda moneda) {
        try {
            return RoundingMode.valueOf(moneda.reglaRedondeo());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "La moneda " + moneda.codigoIso() + " tiene una regla de redondeo inválida.",
                    exception);
        }
    }
}
