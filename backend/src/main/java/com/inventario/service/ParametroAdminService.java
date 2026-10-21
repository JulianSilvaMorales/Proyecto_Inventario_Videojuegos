package com.inventario.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.inventario.domain.ParametroFila;
import com.inventario.dto.SolicitudActualizacionParametro;
import com.inventario.dto.SolicitudParametro;
import com.inventario.repository.ParametroAdminRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParametroAdminService {

    private static final Set<String> TIPOS = Set.of("TEXTO", "ENTERO", "DECIMAL", "BOOLEANO", "JSON");
    private static final Set<String> AMBITOS = Set.of("NEGOCIO", "PAIS");

    private final ParametroAdminRepository repository;
    private final ParametroService parametrosResueltos;
    private final AuditoriaService auditoria;
    private final AccesoPaisService accesoPais;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ParametroAdminService(
            ParametroAdminRepository repository,
            ParametroService parametrosResueltos,
            AuditoriaService auditoria,
            AccesoPaisService accesoPais,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.parametrosResueltos = parametrosResueltos;
        this.auditoria = auditoria;
        this.accesoPais = accesoPais;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ParametroFila> listar(
            String clave,
            String ambito,
            Set<String> paisesPermitidos,
            Pageable pageable) {
        return repository.listar(clave, ambito, paisesPermitidos, pageable);
    }

    @Transactional(readOnly = true)
    public ParametroFila obtener(UUID id, Set<String> paisesPermitidos) {
        ParametroFila parametro = buscar(id);
        validarAlcancePais(parametro, paisesPermitidos);
        return parametro;
    }

    @Transactional
    public ParametroFila crear(SolicitudParametro solicitud, UUID actorId, Set<String> paisesPermitidos) {
        JsonNode valor = validarContenido(
                solicitud.clave(),
                solicitud.valor(),
                solicitud.tipo(),
                solicitud.minimo(),
                solicitud.maximo(),
                solicitud.ambito(),
                solicitud.paisId(),
                solicitud.vigenteDesde(),
                solicitud.vigenteHasta(),
                paisesPermitidos);
        validarSuperposicion(solicitud.clave(), solicitud.ambito(), solicitud.paisId(),
                solicitud.vigenteDesde(), solicitud.vigenteHasta(), null);

        ParametroFila nueva = new ParametroFila(
                null,
                solicitud.clave().trim(),
                valor,
                solicitud.tipo(),
                solicitud.minimo(),
                solicitud.maximo(),
                valor,
                solicitud.ambito(),
                solicitud.paisId(),
                null,
                solicitud.vigenteDesde(),
                solicitud.vigenteHasta(),
                null,
                null);
        UUID id = repository.crear(nueva, actorId, ahora());
        parametrosResueltos.invalidarTodo();
        ParametroFila creada = buscar(id);
        auditoria.registrar(actorId, solicitud.paisId(), "CREAR", "parametro", id, null, creada);
        return creada;
    }

    @Transactional
    public ParametroFila actualizar(
            UUID id,
            SolicitudActualizacionParametro solicitud,
            UUID actorId,
            Set<String> paisesPermitidos) {
        ParametroFila anterior = buscar(id);
        validarAlcancePais(anterior, paisesPermitidos);

        JsonNode valor = validarContenido(
                anterior.clave(),
                solicitud.valor(),
                anterior.tipo(),
                solicitud.minimo(),
                solicitud.maximo(),
                solicitud.ambito(),
                solicitud.paisId(),
                solicitud.vigenteDesde(),
                solicitud.vigenteHasta(),
                paisesPermitidos);
        validarSuperposicion(anterior.clave(), solicitud.ambito(), solicitud.paisId(),
                solicitud.vigenteDesde(), solicitud.vigenteHasta(), id);

        ParametroFila cambios = new ParametroFila(
                id,
                anterior.clave(),
                valor,
                anterior.tipo(),
                solicitud.minimo(),
                solicitud.maximo(),
                anterior.valorPorDefecto(),
                solicitud.ambito(),
                solicitud.paisId(),
                anterior.paisCodigo(),
                solicitud.vigenteDesde(),
                solicitud.vigenteHasta(),
                null,
                null);
        repository.actualizar(id, cambios, actorId, ahora());
        parametrosResueltos.invalidarTodo();
        ParametroFila actualizada = buscar(id);
        auditoria.registrar(actorId, actualizada.paisId(), "ACTUALIZAR", "parametro", id, anterior, actualizada);
        return actualizada;
    }

    private JsonNode validarContenido(
            String clave,
            Object valor,
            String tipo,
            BigDecimal minimo,
            BigDecimal maximo,
            String ambito,
            UUID paisId,
            java.time.LocalDate vigenteDesde,
            java.time.LocalDate vigenteHasta,
            Set<String> paisesPermitidos) {
        if (tipo == null || !TIPOS.contains(tipo)) {
            throw error(HttpStatus.BAD_REQUEST, "error.parametro.tipo");
        }
        if (ambito == null || !AMBITOS.contains(ambito)) {
            throw error(HttpStatus.BAD_REQUEST, "error.parametro.ambito");
        }
        if (vigenteHasta != null && vigenteHasta.isBefore(vigenteDesde)) {
            throw error(HttpStatus.BAD_REQUEST, "error.parametro.vigencia");
        }
        if ("NEGOCIO".equals(ambito) && paisId != null) {
            throw error(HttpStatus.BAD_REQUEST, "error.parametro.ambito-pais");
        }
        if ("PAIS".equals(ambito)) {
            if (paisId == null) {
                throw error(HttpStatus.BAD_REQUEST, "error.parametro.ambito-pais");
            }
            String codigoPais = repository.codigoPais(paisId)
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "error.parametro.pais-not-found"));
            accesoPais.validar(paisesPermitidos, codigoPais);
        }

        JsonNode nodo = objectMapper.valueToTree(valor);
        switch (tipo) {
            case "TEXTO" -> {
                if (!nodo.isTextual() || nodo.asText().isBlank()) {
                    throw error(HttpStatus.BAD_REQUEST, "error.parametro.valor-texto");
                }
            }
            case "ENTERO" -> {
                if (!nodo.isIntegralNumber()) {
                    throw error(HttpStatus.BAD_REQUEST, "error.parametro.valor-entero");
                }
            }
            case "DECIMAL" -> {
                // La API transporta los decimales como texto; se convierten sin pasar por double.
                nodo = decimalDesdeTexto(nodo);
                if (!nodo.isNumber()) {
                    throw error(HttpStatus.BAD_REQUEST, "error.parametro.valor-decimal");
                }
            }
            case "BOOLEANO" -> {
                if (!nodo.isBoolean()) {
                    throw error(HttpStatus.BAD_REQUEST, "error.parametro.valor-booleano");
                }
            }
            default -> {
                // JSON admite cualquier nodo válido; valueToTree ya lo produjo.
            }
        }

        if (minimo != null || maximo != null) {
            if (!"ENTERO".equals(tipo) && !"DECIMAL".equals(tipo)) {
                throw error(HttpStatus.BAD_REQUEST, "error.parametro.min-max-tipo");
            }
            BigDecimal numerico = nodo.decimalValue();
            if (minimo != null && numerico.compareTo(minimo) < 0) {
                throw error(HttpStatus.BAD_REQUEST, "error.parametro.valor-fuera-rango");
            }
            if (maximo != null && numerico.compareTo(maximo) > 0) {
                throw error(HttpStatus.BAD_REQUEST, "error.parametro.valor-fuera-rango");
            }
        }

        if (clave == null || clave.isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "error.parametro.clave");
        }
        return nodo;
    }

    private static JsonNode decimalDesdeTexto(JsonNode nodo) {
        if (!nodo.isTextual()) {
            return nodo;
        }
        try {
            return DecimalNode.valueOf(new BigDecimal(nodo.asText().trim()));
        } catch (NumberFormatException exception) {
            return nodo;
        }
    }

    private void validarSuperposicion(
            String clave,
            String ambito,
            UUID paisId,
            java.time.LocalDate vigenteDesde,
            java.time.LocalDate vigenteHasta,
            UUID excluirId) {
        if (repository.buscarSuperpuesto(
                clave.trim(), ambito, paisId, vigenteDesde, vigenteHasta, excluirId)
                .isPresent()) {
            throw error(HttpStatus.CONFLICT, "error.parametro.superpuesta");
        }
    }

    private void validarAlcancePais(ParametroFila parametro, Set<String> paisesPermitidos) {
        if ("PAIS".equals(parametro.ambito()) && parametro.paisCodigo() != null) {
            accesoPais.validar(paisesPermitidos, parametro.paisCodigo());
        }
    }

    private ParametroFila buscar(UUID id) {
        return repository.buscar(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "error.parametro.not-found"));
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ErrorAplicacionException error(HttpStatus status, String messageKey) {
        return new ErrorAplicacionException(status, messageKey);
    }
}
