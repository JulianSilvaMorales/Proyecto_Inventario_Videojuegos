package com.inventario.service;

import com.inventario.domain.AjusteInventario;
import com.inventario.domain.MovimientoInventario;
import com.inventario.dto.SolicitudAjusteInventario;
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
    private final AuditoriaService auditoria;
    private final Clock clock;

    public AjustesService(
            InventarioRepository repository,
            AuditoriaService auditoria,
            Clock clock) {
        this.repository = repository;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AjusteInventario> listar(
            String estado,
            String tipo,
            UUID productoId,
            UUID bodegaId,
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
                estadoNormalizado, tipoNormalizado, productoId, bodegaId, pageable);
    }

    @Transactional(readOnly = true)
    public AjusteInventario obtener(UUID id) {
        return ajusteExistente(id);
    }

    @Transactional
    public AjusteInventario solicitar(
            String tipo,
            SolicitudAjusteInventario solicitud,
            UUID actorId) {
        if (!TIPOS.contains(tipo)) {
            throw error(HttpStatus.BAD_REQUEST, "error.inventory.adjustment.type");
        }
        if (!repository.productoExiste(solicitud.productoId())) {
            throw error(HttpStatus.NOT_FOUND, "error.catalog.product.not-found");
        }
        if (repository.buscarBodega(solicitud.bodegaId()).isEmpty()) {
            throw error(HttpStatus.NOT_FOUND, "error.inventory.warehouse.not-found");
        }
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
        AjusteInventario creado = ajusteExistente(id);
        auditoria.registrar(
                actorId, null, "SOLICITAR", "ajuste_inventario", id, null, creado);
        return creado;
    }

    @Transactional
    public AjusteInventario aprobar(UUID id, UUID actorId) {
        repository.bloquearAjuste(id);
        AjusteInventario anterior = ajusteExistente(id);
        if (!"PENDIENTE_DE_APROBACION".equals(anterior.estado())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.adjustment.transition");
        }
        if (!repository.actualizarAjusteAprobado(id, actorId, ahora())) {
            throw error(HttpStatus.CONFLICT, "error.inventory.adjustment.transition");
        }
        AjusteInventario aprobado = ajusteExistente(id);
        auditoria.registrar(
                actorId, null, "APROBAR", "ajuste_inventario", id, anterior, aprobado);
        return aprobado;
    }

    @Transactional
    public AjusteInventario aplicar(UUID id, UUID actorId) {
        repository.bloquearAjuste(id);
        AjusteInventario ajuste = ajusteExistente(id);
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
        AjusteInventario aplicado = ajusteExistente(id);
        auditoria.registrar(
                actorId, null, "APLICAR", "ajuste_inventario", id, ajuste, aplicado);
        return aplicado;
    }

    @Transactional
    public AjusteInventario revalidar(UUID id, UUID actorId) {
        repository.bloquearAjuste(id);
        AjusteInventario anterior = ajusteExistente(id);
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
        AjusteInventario actualizado = ajusteExistente(id);
        auditoria.registrar(
                actorId, null, "REVALIDAR", "ajuste_inventario", id, anterior, actualizado);
        return actualizado;
    }

    private AjusteInventario ajusteExistente(UUID id) {
        return repository.buscarAjuste(id)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "error.inventory.adjustment.not-found"));
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ErrorAplicacionException error(HttpStatus status, String messageKey) {
        return new ErrorAplicacionException(status, messageKey);
    }
}
