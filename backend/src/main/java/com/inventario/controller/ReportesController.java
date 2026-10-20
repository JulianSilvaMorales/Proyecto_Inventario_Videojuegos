package com.inventario.controller;

import com.inventario.domain.MovimientoInventario;
import com.inventario.domain.SaldoInventario;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.service.ReportesService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/reportes/inventario")
@PreAuthorize("hasAnyAuthority('REPORTES_TODOS', 'REPORTES_INVENTARIO')")
public class ReportesController {

    private static final MediaType CSV_UTF8 = MediaType.parseMediaType("text/csv; charset=UTF-8");

    private final ReportesService reportes;

    public ReportesController(ReportesService reportes) {
        this.reportes = reportes;
    }

    @GetMapping("/existencias")
    public Page<SaldoInventario> existencias(
            @RequestParam(required = false) UUID productoId,
            @RequestParam(required = false) UUID bodegaId,
            @RequestParam(defaultValue = "false") boolean soloConExistencia,
            @RequestParam(required = false) UUID paisId,
            Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return reportes.listarExistencias(
                productoId,
                bodegaId,
                paisId,
                actor.paises(),
                soloConExistencia,
                pageable);
    }

    @GetMapping(value = "/existencias.csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportarExistencias(
            @RequestParam(required = false) UUID productoId,
            @RequestParam(required = false) UUID bodegaId,
            @RequestParam(defaultValue = "false") boolean soloConExistencia,
            @RequestParam(required = false) UUID paisId,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        StreamingResponseBody body = output -> reportes.exportarExistencias(
                productoId,
                bodegaId,
                paisId,
                actor.paises(),
                soloConExistencia,
                output);
        return descarga("existencias.csv", body);
    }

    @GetMapping("/movimientos")
    public Page<MovimientoInventario> movimientos(
            @RequestParam(required = false) UUID productoId,
            @RequestParam(required = false) UUID bodegaId,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) OffsetDateTime desde,
            @RequestParam(required = false) OffsetDateTime hasta,
            @RequestParam(required = false) UUID paisId,
            @RequestParam(required = false) UUID monedaId,
            Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return reportes.listarMovimientos(
                productoId,
                bodegaId,
                usuarioId,
                tipo,
                desde,
                hasta,
                paisId,
                monedaId,
                actor.paises(),
                pageable);
    }

    @GetMapping(value = "/movimientos.csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportarMovimientos(
            @RequestParam(required = false) UUID productoId,
            @RequestParam(required = false) UUID bodegaId,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) OffsetDateTime desde,
            @RequestParam(required = false) OffsetDateTime hasta,
            @RequestParam(required = false) UUID paisId,
            @RequestParam(required = false) UUID monedaId,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        StreamingResponseBody body = output -> reportes.exportarMovimientos(
                productoId,
                bodegaId,
                usuarioId,
                tipo,
                desde,
                hasta,
                paisId,
                monedaId,
                actor.paises(),
                output);
        return descarga("movimientos.csv", body);
    }

    private ResponseEntity<StreamingResponseBody> descarga(
            String nombre, StreamingResponseBody body) {
        return ResponseEntity.ok()
                .contentType(CSV_UTF8)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombre + "\"")
                .body(body);
    }
}
