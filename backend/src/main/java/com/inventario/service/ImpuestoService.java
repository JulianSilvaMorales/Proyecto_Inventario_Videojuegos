package com.inventario.service;

import com.inventario.domain.CalculoImpuesto;
import com.inventario.domain.Impuesto;
import com.inventario.domain.Moneda;
import com.inventario.repository.ConfiguracionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImpuestoService {

    private final ConfiguracionRepository repository;

    public ImpuestoService(ConfiguracionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CalculoImpuesto calcular(
            UUID paisId,
            UUID categoriaId,
            UUID monedaId,
            BigDecimal precio,
            LocalDate fecha) {
        Impuesto tax = repository.buscarImpuestoVigente(paisId, categoriaId, fecha)
                .orElseThrow(() -> new ErrorAplicacionException(
                        HttpStatus.NOT_FOUND,
                        "error.impuesto.missing"));
        Moneda currency = repository.buscarMoneda(monedaId)
                .orElseThrow(() -> new ErrorAplicacionException(
                        HttpStatus.NOT_FOUND,
                        "error.moneda.not-found"));
        RoundingMode roundingMode = roundingMode(currency);
        int decimals = currency.decimales();
        BigDecimal price = precio.setScale(decimals, roundingMode);
        BigDecimal base;
        BigDecimal taxAmount;
        BigDecimal total;

        if (tax.incluidoEnPrecio()) {
            base = price.divide(
                            BigDecimal.ONE.add(tax.tasa()),
                            decimals + 8,
                            roundingMode)
                    .setScale(decimals, roundingMode);
            taxAmount = price.subtract(base).setScale(decimals, roundingMode);
            total = price;
        } else {
            base = price;
            taxAmount = base.multiply(tax.tasa()).setScale(decimals, roundingMode);
            total = base.add(taxAmount).setScale(decimals, roundingMode);
        }

        return new CalculoImpuesto(
                tax.id(),
                tax.nombre(),
                tax.tasa(),
                tax.incluidoEnPrecio(),
                price,
                base,
                taxAmount,
                total,
                currency.codigoIso(),
                fecha);
    }

    private RoundingMode roundingMode(Moneda currency) {
        try {
            return RoundingMode.valueOf(currency.reglaRedondeo());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "La moneda " + currency.codigoIso() + " tiene una regla de redondeo inválida.",
                    exception);
        }
    }
}
