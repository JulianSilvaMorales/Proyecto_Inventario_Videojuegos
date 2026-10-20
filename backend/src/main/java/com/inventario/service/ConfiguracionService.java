package com.inventario.service;

import com.inventario.domain.Idioma;
import com.inventario.domain.Impuesto;
import com.inventario.domain.Moneda;
import com.inventario.domain.Pais;
import com.inventario.domain.TipoCambio;
import com.inventario.dto.SolicitudIdioma;
import com.inventario.dto.SolicitudImpuesto;
import com.inventario.dto.SolicitudMoneda;
import com.inventario.dto.SolicitudPais;
import com.inventario.dto.SolicitudTipoCambio;
import com.inventario.repository.ConfiguracionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfiguracionService {

    private final ConfiguracionRepository repository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public ConfiguracionService(
            ConfiguracionRepository repository,
            AuditoriaService auditoriaService,
            Clock clock) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Pais> listarPaises(String buscar, Pageable pageable) {
        return repository.listarPaises(buscar, pageable);
    }

    @Transactional(readOnly = true)
    public Pais obtenerPais(UUID id) {
        return requerir(repository.buscarPais(id), HttpStatus.NOT_FOUND, "error.pais.not-found");
    }

    @Transactional
    public Pais crearPais(SolicitudPais solicitud, UUID actorId) {
        Pais pais = pais(null, solicitud);
        if (pais.habilitado()) {
            throw new ErrorAplicacionException(
                    HttpStatus.BAD_REQUEST,
                    "error.pais.create-enabled");
        }
        UUID id = repository.crearPais(pais);
        Pais created = obtenerPais(id);
        auditoriaService.registrar(actorId, null, "CREAR", "pais", id, null, created);
        return created;
    }

    @Transactional
    public Pais actualizarPais(UUID id, SolicitudPais solicitud, UUID actorId) {
        Pais anterior = obtenerPais(id);
        Pais nuevo = pais(id, solicitud);
        if (nuevo.habilitado()) {
            validarPaisHabilitable(nuevo);
        }
        confirmarActualizacion(
                repository.actualizarPais(nuevo),
                "error.pais.not-found");
        Pais actualizado = obtenerPais(id);
        auditoriaService.registrar(actorId, null, "ACTUALIZAR", "pais", id, anterior, actualizado);
        return actualizado;
    }

    @Transactional
    public void eliminarPais(UUID id, UUID actorId) {
        Pais anterior = obtenerPais(id);
        confirmarEliminacion(repository.eliminarPais(id), "error.pais.not-found");
        auditoriaService.registrar(actorId, null, "ELIMINAR", "pais", id, anterior, null);
    }

    @Transactional(readOnly = true)
    public Page<Moneda> listarMonedas(String buscar, Pageable pageable) {
        return repository.listarMonedas(buscar, pageable);
    }

    @Transactional(readOnly = true)
    public Moneda obtenerMoneda(UUID id) {
        return requerir(repository.buscarMoneda(id), HttpStatus.NOT_FOUND, "error.moneda.not-found");
    }

    @Transactional
    public Moneda crearMoneda(SolicitudMoneda solicitud, UUID actorId) {
        Moneda nueva = moneda(null, solicitud);
        UUID id = repository.crearMoneda(nueva);
        Moneda creada = obtenerMoneda(id);
        auditoriaService.registrar(actorId, null, "CREAR", "moneda", id, null, creada);
        return creada;
    }

    @Transactional
    public Moneda actualizarMoneda(UUID id, SolicitudMoneda solicitud, UUID actorId) {
        Moneda anterior = obtenerMoneda(id);
        confirmarActualizacion(
                repository.actualizarMoneda(moneda(id, solicitud)),
                "error.moneda.not-found");
        Moneda actualizada = obtenerMoneda(id);
        auditoriaService.registrar(
                actorId, null, "ACTUALIZAR", "moneda", id, anterior, actualizada);
        return actualizada;
    }

    @Transactional
    public void eliminarMoneda(UUID id, UUID actorId) {
        Moneda anterior = obtenerMoneda(id);
        confirmarEliminacion(repository.eliminarMoneda(id), "error.moneda.not-found");
        auditoriaService.registrar(actorId, null, "ELIMINAR", "moneda", id, anterior, null);
    }

    @Transactional(readOnly = true)
    public Page<Idioma> listarIdiomas(String buscar, Pageable pageable) {
        return repository.listarIdiomas(buscar, pageable);
    }

    @Transactional(readOnly = true)
    public Idioma obtenerIdioma(UUID id) {
        return requerir(repository.buscarIdioma(id), HttpStatus.NOT_FOUND, "error.idioma.not-found");
    }

    @Transactional
    public Idioma crearIdioma(SolicitudIdioma solicitud, UUID actorId) {
        Idioma nuevo = idioma(null, solicitud);
        UUID id = repository.crearIdioma(nuevo);
        Idioma creado = obtenerIdioma(id);
        auditoriaService.registrar(actorId, null, "CREAR", "idioma", id, null, creado);
        return creado;
    }

    @Transactional
    public Idioma actualizarIdioma(UUID id, SolicitudIdioma solicitud, UUID actorId) {
        Idioma anterior = obtenerIdioma(id);
        confirmarActualizacion(
                repository.actualizarIdioma(idioma(id, solicitud)),
                "error.idioma.not-found");
        Idioma actualizado = obtenerIdioma(id);
        auditoriaService.registrar(
                actorId, null, "ACTUALIZAR", "idioma", id, anterior, actualizado);
        return actualizado;
    }

    @Transactional
    public void eliminarIdioma(UUID id, UUID actorId) {
        Idioma anterior = obtenerIdioma(id);
        confirmarEliminacion(repository.eliminarIdioma(id), "error.idioma.not-found");
        auditoriaService.registrar(actorId, null, "ELIMINAR", "idioma", id, anterior, null);
    }

    @Transactional(readOnly = true)
    public Page<TipoCambio> listarTiposCambio(String buscar, Pageable pageable) {
        return repository.listarTiposCambio(buscar, pageable);
    }

    @Transactional(readOnly = true)
    public TipoCambio obtenerTipoCambio(UUID id) {
        return requerir(
                repository.buscarTipoCambio(id),
                HttpStatus.NOT_FOUND,
                "error.tipo-cambio.not-found");
    }

    @Transactional
    public TipoCambio crearTipoCambio(SolicitudTipoCambio solicitud, UUID actorId) {
        TipoCambio nuevo = tipoCambio(null, solicitud, actorId);
        UUID id = repository.crearTipoCambio(nuevo);
        TipoCambio creado = obtenerTipoCambio(id);
        auditoriaService.registrar(actorId, null, "CREAR", "tipo_cambio", id, null, creado);
        return creado;
    }

    @Transactional
    public TipoCambio actualizarTipoCambio(
            UUID id,
            SolicitudTipoCambio solicitud,
            UUID actorId) {
        TipoCambio anterior = obtenerTipoCambio(id);
        confirmarActualizacion(
                repository.actualizarTipoCambio(tipoCambio(id, solicitud, actorId)),
                "error.tipo-cambio.not-found");
        TipoCambio actualizado = obtenerTipoCambio(id);
        auditoriaService.registrar(
                actorId, null, "ACTUALIZAR", "tipo_cambio", id, anterior, actualizado);
        return actualizado;
    }

    @Transactional
    public void eliminarTipoCambio(UUID id, UUID actorId) {
        TipoCambio anterior = obtenerTipoCambio(id);
        confirmarEliminacion(
                repository.eliminarTipoCambio(id),
                "error.tipo-cambio.not-found");
        auditoriaService.registrar(
                actorId, null, "ELIMINAR", "tipo_cambio", id, anterior, null);
    }

    @Transactional(readOnly = true)
    public Page<Impuesto> listarImpuestos(String buscar, Pageable pageable) {
        return repository.listarImpuestos(buscar, pageable);
    }

    @Transactional(readOnly = true)
    public Impuesto obtenerImpuesto(UUID id) {
        return requerir(repository.buscarImpuesto(id), HttpStatus.NOT_FOUND, "error.impuesto.not-found");
    }

    @Transactional
    public Impuesto crearImpuesto(SolicitudImpuesto solicitud, UUID actorId) {
        Impuesto nuevo = impuesto(null, solicitud);
        validarPeriodoImpuesto(nuevo);
        UUID id = repository.crearImpuesto(nuevo);
        Impuesto creado = obtenerImpuesto(id);
        auditoriaService.registrar(
                actorId, creado.paisId(), "CREAR", "impuesto", id, null, creado);
        return creado;
    }

    @Transactional
    public Impuesto actualizarImpuesto(
            UUID id,
            SolicitudImpuesto solicitud,
            UUID actorId) {
        Impuesto anterior = obtenerImpuesto(id);
        Impuesto nuevo = impuesto(id, solicitud);
        validarPeriodoImpuesto(nuevo);
        confirmarActualizacion(
                repository.actualizarImpuesto(nuevo),
                "error.impuesto.not-found");
        Impuesto actualizado = obtenerImpuesto(id);
        auditoriaService.registrar(
                actorId,
                actualizado.paisId(),
                "ACTUALIZAR",
                "impuesto",
                id,
                anterior,
                actualizado);
        return actualizado;
    }

    @Transactional
    public void eliminarImpuesto(UUID id, UUID actorId) {
        Impuesto anterior = obtenerImpuesto(id);
        confirmarEliminacion(repository.eliminarImpuesto(id), "error.impuesto.not-found");
        auditoriaService.registrar(
                actorId, anterior.paisId(), "ELIMINAR", "impuesto", id, anterior, null);
    }

    private void validarPaisHabilitable(Pais pais) {
        if (pais.monedaLocalId() == null
                || pais.idiomaPredeterminadoId() == null
                || pais.zonaHoraria() == null
                || pais.zonaHoraria().isBlank()) {
            throw new ErrorAplicacionException(
                    HttpStatus.BAD_REQUEST,
                    "error.pais.enable-configuration");
        }
        obtenerMoneda(pais.monedaLocalId());
        Idioma idioma = obtenerIdioma(pais.idiomaPredeterminadoId());
        if (!idioma.habilitado()) {
            throw new ErrorAplicacionException(
                    HttpStatus.BAD_REQUEST,
                    "error.pais.enable-language");
        }
        try {
            ZoneId.of(pais.zonaHoraria());
        } catch (DateTimeException exception) {
            throw new ErrorAplicacionException(
                    HttpStatus.BAD_REQUEST,
                    "error.pais.enable-timezone");
        }
        UUID id = pais.id();
        if (id == null || !repository.existeImpuestoVigente(id, LocalDate.now(clock))) {
            throw new ErrorAplicacionException(
                    HttpStatus.BAD_REQUEST,
                    "error.pais.enable-tax");
        }
    }

    private void validarPeriodoImpuesto(Impuesto impuesto) {
        if (impuesto.vigenteHasta() != null
                && impuesto.vigenteHasta().isBefore(impuesto.vigenteDesde())) {
            throw new ErrorAplicacionException(
                    HttpStatus.BAD_REQUEST,
                    "error.impuesto.dates");
        }
        if (repository.existeSolapamientoImpuesto(impuesto)) {
            throw new ErrorAplicacionException(
                    HttpStatus.CONFLICT,
                    "error.impuesto.overlap");
        }
    }

    private Pais pais(UUID id, SolicitudPais solicitud) {
        return new Pais(
                id,
                solicitud.codigoIso().trim().toUpperCase(Locale.ROOT),
                solicitud.nombre().trim(),
                solicitud.monedaLocalId(),
                solicitud.idiomaPredeterminadoId(),
                solicitud.zonaHoraria() == null || solicitud.zonaHoraria().isBlank()
                        ? null
                        : solicitud.zonaHoraria().trim(),
                solicitud.habilitado());
    }

    private Moneda moneda(UUID id, SolicitudMoneda solicitud) {
        String roundingRule = solicitud.reglaRedondeo().trim().toUpperCase(Locale.ROOT);
        try {
            RoundingMode.valueOf(roundingRule);
        } catch (IllegalArgumentException exception) {
            throw new ErrorAplicacionException(
                    HttpStatus.BAD_REQUEST,
                    "error.moneda.rounding");
        }
        Locale locale = Locale.forLanguageTag(solicitud.formato().trim());
        if (locale.getLanguage().isEmpty() || locale.toLanguageTag().equals("und")) {
            throw new ErrorAplicacionException(
                    HttpStatus.BAD_REQUEST,
                    "error.moneda.locale");
        }
        return new Moneda(
                id,
                solicitud.codigoIso().trim().toUpperCase(Locale.ROOT),
                solicitud.nombre().trim(),
                solicitud.simbolo().trim(),
                solicitud.decimales(),
                locale.toLanguageTag(),
                roundingRule);
    }

    private Idioma idioma(UUID id, SolicitudIdioma solicitud) {
        String code = solicitud.codigoBcp47().trim();
        Locale locale = Locale.forLanguageTag(code);
        if (locale.getLanguage().isEmpty() || locale.toLanguageTag().equals("und")) {
            throw new ErrorAplicacionException(
                    HttpStatus.BAD_REQUEST,
                    "error.idioma.code");
        }
        return new Idioma(id, locale.toLanguageTag(), solicitud.nombre().trim(), solicitud.habilitado());
    }

    private TipoCambio tipoCambio(
            UUID id,
            SolicitudTipoCambio solicitud,
            UUID actorId) {
        if (solicitud.monedaOrigenId().equals(solicitud.monedaDestinoId())) {
            throw new ErrorAplicacionException(
                    HttpStatus.BAD_REQUEST,
                    "error.tipo-cambio.pair");
        }
        if (solicitud.tasa().signum() <= 0) {
            throw new ErrorAplicacionException(
                    HttpStatus.BAD_REQUEST,
                    "error.tipo-cambio.positive");
        }
        return new TipoCambio(
                id,
                solicitud.monedaOrigenId(),
                null,
                solicitud.monedaDestinoId(),
                null,
                solicitud.tasa(),
                solicitud.vigenteDesdeUtc(),
                "MANUAL",
                actorId);
    }

    private Impuesto impuesto(UUID id, SolicitudImpuesto solicitud) {
        return new Impuesto(
                id,
                solicitud.paisId(),
                solicitud.categoriaId(),
                solicitud.nombre().trim(),
                solicitud.tasa(),
                solicitud.incluidoEnPrecio(),
                solicitud.vigenteDesde(),
                solicitud.vigenteHasta());
    }

    private <T> T requerir(
            java.util.Optional<T> value,
            HttpStatus status,
            String messageKey) {
        return value.orElseThrow(() -> new ErrorAplicacionException(status, messageKey));
    }

    private void confirmarActualizacion(boolean updated, String messageKey) {
        if (!updated) {
            throw new ErrorAplicacionException(HttpStatus.NOT_FOUND, messageKey);
        }
    }

    private void confirmarEliminacion(boolean deleted, String messageKey) {
        if (!deleted) {
            throw new ErrorAplicacionException(HttpStatus.NOT_FOUND, messageKey);
        }
    }
}
