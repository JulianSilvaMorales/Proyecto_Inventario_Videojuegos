package com.inventario.controller;

import com.inventario.domain.Bodega;
import com.inventario.domain.MovimientoInventario;
import com.inventario.domain.SaldoInventario;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.SolicitudBodega;
import com.inventario.dto.SolicitudSalidaInventario;
import com.inventario.service.InventarioService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/inventario")
@PreAuthorize("hasAuthority('INVENTARIO_CONSULTAR')")
public class InventarioController {

    private final InventarioService inventarioService;

    public InventarioController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping("/bodegas")
    public Page<Bodega> listarBodegas(
            @RequestParam(required = false) String buscar,
            Pageable pageable) {
        return inventarioService.listarBodegas(buscar, pageable);
    }

    @GetMapping("/bodegas/{id}")
    public Bodega obtenerBodega(@PathVariable UUID id) {
        return inventarioService.obtenerBodega(id);
    }

    @PostMapping("/bodegas")
    @PreAuthorize("hasAuthority('COMPRAS_GESTIONAR')")
    public ResponseEntity<Bodega> crearBodega(
            @Valid @RequestBody SolicitudBodega solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        Bodega bodega = inventarioService.crearBodega(solicitud, actor.usuarioId());
        return creado("/bodegas", bodega.id(), bodega);
    }

    @PutMapping("/bodegas/{id}")
    @PreAuthorize("hasAuthority('COMPRAS_GESTIONAR')")
    public Bodega actualizarBodega(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudBodega solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return inventarioService.actualizarBodega(id, solicitud, actor.usuarioId());
    }

    @GetMapping("/saldos")
    public Page<SaldoInventario> listarSaldos(
            @RequestParam(required = false) UUID productoId,
            @RequestParam(required = false) UUID bodegaId,
            @RequestParam(defaultValue = "false") boolean soloConExistencia,
            Pageable pageable) {
        return inventarioService.listarSaldos(
                productoId, bodegaId, soloConExistencia, pageable);
    }

    @PostMapping("/salidas")
    @PreAuthorize("hasAuthority('SALIDAS_INVENTARIO_GESTIONAR')")
    public ResponseEntity<MovimientoInventario> registrarSalida(
            @Valid @RequestBody SolicitudSalidaInventario solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        MovimientoInventario salida =
                inventarioService.registrarSalida(solicitud, actor.usuarioId());
        return creado("/movimientos", salida.id(), salida);
    }

    @GetMapping("/movimientos")
    public Page<MovimientoInventario> listarMovimientos(
            @RequestParam(required = false) UUID productoId,
            @RequestParam(required = false) UUID bodegaId,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) OffsetDateTime desde,
            @RequestParam(required = false) OffsetDateTime hasta,
            Pageable pageable) {
        return inventarioService.listarMovimientos(
                productoId, bodegaId, usuarioId, tipo, desde, hasta, pageable);
    }

    @GetMapping("/movimientos/{id}")
    public MovimientoInventario obtenerMovimiento(@PathVariable UUID id) {
        return inventarioService.obtenerMovimiento(id);
    }

    private <T> ResponseEntity<T> creado(String path, UUID id, T body) {
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/v1/inventario" + path)
                .replaceQuery(null)
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(body);
    }
}
