package com.inventario.service;

import com.inventario.domain.Cliente;
import com.inventario.domain.Pais;
import com.inventario.dto.SolicitudCliente;
import com.inventario.repository.ClienteRepository;
import com.inventario.repository.ConfiguracionRepository;
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
public class ClienteService {

    private final ClienteRepository repository;
    private final ConfiguracionRepository configuracion;
    private final AccesoPaisService accesoPais;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ClienteService(
            ClienteRepository repository,
            ConfiguracionRepository configuracion,
            AccesoPaisService accesoPais,
            AuditoriaService auditoria,
            Clock clock) {
        this.repository = repository;
        this.configuracion = configuracion;
        this.accesoPais = accesoPais;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Cliente> listar(String buscar, Set<String> paisesPermitidos, Pageable pageable) {
        return repository.listar(buscar, paisesPermitidos, pageable);
    }

    @Transactional(readOnly = true)
    public Cliente obtener(UUID id, Set<String> paisesPermitidos) {
        Cliente cliente = requerir(repository.buscar(id));
        validarAcceso(cliente.paisId(), paisesPermitidos);
        return cliente;
    }

    @Transactional
    public Cliente crear(
            SolicitudCliente solicitud,
            UUID actorId,
            Set<String> paisesPermitidos) {
        validarSolicitud(solicitud, true);
        Pais pais = validarPais(solicitud.paisId(), paisesPermitidos, true);
        OffsetDateTime ahora = ahora();
        Cliente nuevo = desdeSolicitud(
                null, pais.id(), solicitud, ahora, ahora);
        UUID id = repository.crear(nuevo);
        Cliente creado = obtener(id, paisesPermitidos);
        auditoria.registrar(
                actorId,
                pais.id(),
                "CREAR",
                "cliente",
                id,
                null,
                resumenAuditoria(creado));
        return creado;
    }

    @Transactional
    public Cliente actualizar(
            UUID id,
            SolicitudCliente solicitud,
            UUID actorId,
            Set<String> paisesPermitidos) {
        repository.bloquear(id);
        Cliente anterior = obtener(id, paisesPermitidos);
        if (!anterior.paisId().equals(solicitud.paisId())) {
            throw error(HttpStatus.CONFLICT, "error.customer.country.immutable");
        }
        validarSolicitud(solicitud, false);
        validarPais(solicitud.paisId(), paisesPermitidos, false);
        OffsetDateTime consentimientoEnUtc = anterior.consentimientoRegistrado()
                        == solicitud.consentimientoRegistrado()
                        && anterior.baseLegal().equals(solicitud.baseLegal().trim())
                ? anterior.consentimientoEnUtc()
                : ahora();
        Cliente actualizado = desdeSolicitud(
                id,
                anterior.paisId(),
                solicitud,
                consentimientoEnUtc,
                anterior.ultimoContactoEnUtc());
        if (!repository.actualizar(actualizado)) {
            throw error(HttpStatus.NOT_FOUND, "error.customer.not-found");
        }
        Cliente resultado = obtener(id, paisesPermitidos);
        auditoria.registrar(
                actorId,
                resultado.paisId(),
                "ACTUALIZAR",
                "cliente",
                id,
                resumenAuditoria(anterior),
                resumenAuditoria(resultado));
        return resultado;
    }

    @Transactional
    public void desactivar(UUID id, UUID actorId, Set<String> paisesPermitidos) {
        repository.bloquear(id);
        Cliente cliente = obtener(id, paisesPermitidos);
        if (!cliente.activo()) {
            return;
        }
        if (repository.desactivar(id)) {
            auditoria.registrar(
                    actorId,
                    cliente.paisId(),
                    "DESACTIVAR",
                    "cliente",
                    id,
                    resumenAuditoria(cliente),
                    resumenAuditoria(new Cliente(
                            cliente.id(),
                            cliente.paisId(),
                            cliente.clasificacion(),
                            cliente.nombre(),
                            cliente.apellidos(),
                            cliente.tipoDocumento(),
                            cliente.documento(),
                            cliente.codigoPostal(),
                            cliente.direccion(),
                            cliente.telefono(),
                            cliente.correo(),
                            cliente.baseLegal(),
                            cliente.consentimientoRegistrado(),
                            cliente.consentimientoEnUtc(),
                            cliente.ultimoContactoEnUtc(),
                            false)));
        }
    }

    private Cliente desdeSolicitud(
            UUID id,
            UUID paisId,
            SolicitudCliente solicitud,
            OffsetDateTime consentimientoEnUtc,
            OffsetDateTime ultimoContactoEnUtc) {
        return new Cliente(
                id,
                paisId,
                solicitud.clasificacion(),
                solicitud.nombre().trim(),
                textoOpcional(solicitud.apellidos()),
                textoOpcional(solicitud.tipoDocumento()),
                textoOpcional(solicitud.documento()),
                textoOpcional(solicitud.codigoPostal()),
                textoOpcional(solicitud.direccion()),
                textoOpcional(solicitud.telefono()),
                solicitud.correo().trim().toLowerCase(),
                solicitud.baseLegal().trim(),
                solicitud.consentimientoRegistrado(),
                consentimientoEnUtc,
                ultimoContactoEnUtc,
                solicitud.activo());
    }

    private void validarSolicitud(SolicitudCliente solicitud, boolean crear) {
        if (crear && !solicitud.consentimientoRegistrado()) {
            throw error(HttpStatus.BAD_REQUEST, "error.customer.consent.required");
        }
        if ("FRECUENTE".equals(solicitud.clasificacion())
                && (textoOpcional(solicitud.apellidos()) == null
                        || textoOpcional(solicitud.codigoPostal()) == null
                        || textoOpcional(solicitud.direccion()) == null
                        || textoOpcional(solicitud.telefono()) == null)) {
            throw error(HttpStatus.BAD_REQUEST, "error.customer.frequent.fields");
        }
        if ("INFRECUENTE".equals(solicitud.clasificacion())
                && textoOpcional(solicitud.documento()) == null) {
            throw error(HttpStatus.BAD_REQUEST, "error.customer.infrequent.document");
        }
    }

    private Pais validarPais(UUID paisId, Set<String> paisesPermitidos, boolean exigirHabilitado) {
        Pais pais = configuracion.buscarPais(paisId)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "error.customer.country.not-found"));
        accesoPais.validar(paisesPermitidos, pais.codigoIso());
        if (exigirHabilitado && !pais.habilitado()) {
            throw error(HttpStatus.BAD_REQUEST, "error.customer.country.disabled");
        }
        return pais;
    }

    private void validarAcceso(UUID paisId, Set<String> paisesPermitidos) {
        Pais pais = configuracion.buscarPais(paisId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "error.customer.not-found"));
        accesoPais.validar(paisesPermitidos, pais.codigoIso());
    }

    private Cliente requerir(java.util.Optional<Cliente> value) {
        return value.orElseThrow(() -> error(HttpStatus.NOT_FOUND, "error.customer.not-found"));
    }

    private static AuditoriaCliente resumenAuditoria(Cliente cliente) {
        return new AuditoriaCliente(
                cliente.id(),
                cliente.paisId(),
                cliente.clasificacion(),
                cliente.activo());
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String textoOpcional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ErrorAplicacionException error(HttpStatus status, String messageKey) {
        return new ErrorAplicacionException(status, messageKey);
    }

    private record AuditoriaCliente(UUID id, UUID paisId, String clasificacion, boolean activo) {}
}
