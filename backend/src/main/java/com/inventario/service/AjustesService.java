package com.inventario.service;

import com.inventario.domain.AjusteInventario;
import com.inventario.domain.Bodega;
import com.inventario.domain.MovimientoInventario;
import com.inventario.domain.Pais;
import com.inventario.dto.SolicitudAjusteInventario;
import com.inventario.repository.ConfiguracionRepository;
import com.inventario.repository.InventarioRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AjustesService {

    private static final Set<String> TIPOS = Set.of("OPERATIVO", "TECNICO");
    private static final Set<String> ESTADOS =
            Set.of("PENDIENTE_DE_APROBACION", "APROBADO", "APLICADO");

    private final InventarioRepository repository;
    private final ConfiguracionRepository configuracion;
    private final AuditoriaService auditoria;
    private final AccesoPaisService accesoPais;
    private final Clock clock;

    public AjustesService(
            InventarioRepository repository,
            ConfiguracionRepository configuracion,
            AuditoriaService auditoria,
            AccesoPaisService accesoPais,
            Clock clock) {
        this.repository = repository;
        this.configuracion = configuracion;
        this.auditoria = auditoria;
        this.accesoPais = accesoPais;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AjusteInventario> listar(
            String estado,
            String tipo,
            UUID productoId,
            UUID bodegaId,
            Set<String> paisesPermitidos,
            Pageable pageable) {
        String estadoNormalizado = estado == null
                ? null
                : estado.trim().toUpperCase(Locale.ROOT);
        String tipoNormalizado = tipo == null
                ? null
                : tipo.trim().toUpperCase(Locale.ROOT);
        if (estadoNormalizado != null && !ESTADOS.contains(estadoNormalizado)) {
            throw error(HttpStatus.BAD_REQUEST, "error.inventory.adjustment.state");
        }
        if (tipoNormalizado != null && !TIPOS.contains(tipoNormalizado)) {
            throw error(HttpStatus.BAD_REQUEST, "error.inventory.adjustment.type");
        }
        return repository.listarAjustes(
                estadoNormalizado,
                tipoNormalizado,
                productoId,
                bodegaId,
                paisesPermitidos,
                pageable);
    }

    @Transactional(readOnly = true)
    public AjusteInventario obtener(UUID id, Set<String> paisesPermitidos) {
        return ajusteExistente(id, paisesPermitidos);
    }

    @Transactional
    public AjusteInventario solicitar(
            String tipo,
            SolicitudAjusteInventario solicitud,
            UUID actorId,
            Set<String> paisesPermitidos) {
        if (!TIPOS.contains(tipo)) {
            throw error(HttpStatus.BAD_REQUEST, "error.inventory.adjustment.type");
        }
        if (!repository.productoExiste(solicitud.productoId())) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.product.not-found");
        }
        Bodega bodega = repository.buscarBodega(solicitud.bodegaId())
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "error.inventory.warehouse.not-found"));
        validarAccesoPais(bodega.paisId(), paisesPermitidos);
        repository.bloquearBodega(solicitud.bodegaId());
        if (!repository.bodegaActiva(solicitud.bodegaId())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.warehouse.inactive");
        }
        OffsetDateTime ahora = ahora();
        int sistema = repository.bloquearSaldo(
                solicitud.productoId(), solicitud.bodegaId(), ahora);
        int diferencia = solicitud.cantidadContada() - sistema;
        AjusteInventario ajuste = new AjusteInventario(
                UUID.randomUUID(),
                solicitud.productoId(),
                null,
                solicitud.bodegaId(),
                null,
                tipo,
                sistema,
                solicitud.cantidadContada(),
                diferencia,
                solicitud.motivo().trim(),
                "PENDIENTE_DE_APROBACION",
                actorId,
                null,
                ahora,
                null,
                null);
        UUID id = repository.crearAjuste(ajuste);
        AjusteInventario creado = ajusteExistente(id, paisesPermitidos);
        auditoria.registrar(
                actorId, null, "SOLICITAR", "ajuste_inventario", id, null, creado);
        return creado;
    }

    @Transactional
    public AjusteInventario aprobar(
            UUID id,
            UUID actorId,
            Set<String> paisesPermitidos) {
        repository.bloquearAjuste(id);
        AjusteInventario anterior = ajusteExistente(id, paisesPermitidos);
        if (!"PENDIENTE_DE_APROBACION".equals(anterior.estado())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.adjustment.transition");
        }
        if (!repository.actualizarAjusteAprobado(id, actorId, ahora())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.adjustment.transition");
        }
        AjusteInventario aprobado = ajusteExistente(id, paisesPermitidos);
        auditoria.registrar(
                actorId, null, "APROBAR", "ajuste_inventario", id, anterior, aprobado);
        return aprobado;
    }

    @Transactional
    public AjusteInventario aplicar(
            UUID id,
            UUID actorId,
            Set<String> paisesPermitidos) {
        repository.bloquearAjuste(id);
        AjusteInventario ajuste = ajusteExistente(id, paisesPermitidos);
        if (!"APROBADO".equals(ajuste.estado())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.adjustment.apply-state");
        }
        OffsetDateTime ahora = ahora();
        int saldoActual = repository.bloquearSaldo(
                ajuste.productoId(), ajuste.bodegaId(), ahora);
        if (saldoActual != ajuste.cantidadSistema()) {
            throw error(HttpStatus.CONFLICT, "error.inventory.adjustment.stock-changed");
        }
        if (ajuste.diferencia() != 0) {
            repository.actualizarSaldo(
                    ajuste.productoId(), ajuste.bodegaId(), ajuste.cantidadContada(), ahora);
            repository.crearMovimiento(new MovimientoInventario(
                    UUID.randomUUID(),
                    ajuste.productoId(),
                    ajuste.codigoProducto(),
                    ajuste.bodegaId(),
                    ajuste.bodegaNombre(),
                    "AJUSTE",
                    Math.abs(ajuste.diferencia()),
                    "AJUSTE",
                    id,
                    actorId,
                    ahora,
                    "Saldo de " + ajuste.cantidadSistema() + " a "
                            + ajuste.cantidadContada() + "; " + ajuste.motivo()));
        }
        if (!repository.actualizarAjusteAplicado(id, ahora)) {
            throw error(HttpStatus.CONFLICT, "error.inventory.adjustment.apply-state");
        }
        AjusteInventario aplicado = ajusteExistente(id, paisesPermitidos);
        auditoria.registrar(
                actorId, null, "APLICAR", "ajuste_inventario", id, ajuste, aplicado);
        return aplicado;
    }

    @Transactional
    public AjusteInventario revalidar(
            UUID id,
            UUID actorId,
            Set<String> paisesPermitidos) {
        repository.bloquearAjuste(id);
        AjusteInventario anterior = ajusteExistente(id, paisesPermitidos);
        if (!"APROBADO".equals(anterior.estado())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.adjustment.revalidate-state");
        }
        int saldoActual = repository.bloquearSaldo(
                anterior.productoId(), anterior.bodegaId(), ahora());
        if (saldoActual == anterior.cantidadSistema()) {
            throw error(HttpStatus.CONFLICT, "error.inventory.adjustment.stock-unchanged");
        }
        if (!repository.actualizarAjusteRevalidado(
                id,
                saldoActual,
                anterior.cantidadContada() - saldoActual)) {
            throw error(HttpStatus.CONFLICT, "error.inventory.adjustment.transition");
        }
        AjusteInventario actualizado = ajusteExistente(id, paisesPermitidos);
        auditoria.registrar(
                actorId, null, "REVALIDAR", "ajuste_inventario", id, anterior, actualizado);
        return actualizado;
    }

    private AjusteInventario ajusteExistente(UUID id, Set<String> paisesPermitidos) {
        AjusteInventario ajuste = repository.buscarAjuste(id)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "error.inventory.adjustment.not-found"));
        Bodega bodega = repository.buscarBodega(ajuste.bodegaId())
                .orElseThrow(() -> new IllegalStateException(
                        "El ajuste referencia una bodega que no existe."));
        validarAccesoPais(bodega.paisId(), paisesPermitidos);
        return ajuste;
    }

    private void validarAccesoPais(UUID paisId, Set<String> paisesPermitidos) {
        Pais pais = paisId == null
                ? null
                : configuracion.buscarPais(paisId)
                        .orElseThrow(() -> new IllegalStateException(
                                "La bodega referencia un país que no existe."));
        accesoPais.validar(paisesPermitidos, pais);
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ErrorAplicacionException error(HttpStatus status, String messageKey) {
        return new ErrorAplicacionException(status, messageKey);
    }
}
