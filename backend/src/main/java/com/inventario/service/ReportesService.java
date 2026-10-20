package com.inventario.service;

import com.inventario.domain.MovimientoInventario;
import com.inventario.domain.Pais;
import com.inventario.domain.SaldoInventario;
import com.inventario.repository.ConfiguracionRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportesService {

    private static final int EXPORT_PAGE_SIZE = 100;

    private final InventarioService inventario;
    private final ConfiguracionRepository configuracion;
    private final AccesoPaisService accesoPais;

    public ReportesService(
            InventarioService inventario,
            ConfiguracionRepository configuracion,
            AccesoPaisService accesoPais) {
        this.inventario = inventario;
        this.configuracion = configuracion;
        this.accesoPais = accesoPais;
    }

    @Transactional(readOnly = true)
    public Page<SaldoInventario> listarExistencias(
            UUID productoId,
            UUID bodegaId,
            UUID paisId,
            Set<String> paisesPermitidos,
            boolean soloConExistencia,
            Pageable pageable) {
        validarPaisFiltro(paisId, paisesPermitidos);
        return inventario.listarSaldos(
                productoId,
                bodegaId,
                paisId,
                paisesPermitidos,
                soloConExistencia,
                pageable);
    }

    @Transactional(readOnly = true)
    public Page<MovimientoInventario> listarMovimientos(
            UUID productoId,
            UUID bodegaId,
            UUID usuarioId,
            String tipo,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            UUID paisId,
            UUID monedaId,
            Set<String> paisesPermitidos,
            Pageable pageable) {
        validarPaisFiltro(paisId, paisesPermitidos);
        return inventario.listarMovimientos(
                productoId,
                bodegaId,
                usuarioId,
                tipo,
                desde,
                hasta,
                paisId,
                monedaId,
                paisesPermitidos,
                pageable);
    }

    @Transactional(readOnly = true)
    public void exportarExistencias(
            UUID productoId,
            UUID bodegaId,
            UUID paisId,
            Set<String> paisesPermitidos,
            boolean soloConExistencia,
            OutputStream output) throws IOException {
        validarPaisFiltro(paisId, paisesPermitidos);
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        escribirFila(
                writer,
                "codigo_producto",
                "nombre_producto",
                "bodega_codigo",
                "bodega_nombre",
                "cantidad",
                "actualizado_en_utc");
        Pageable pageable = PageRequest.of(0, EXPORT_PAGE_SIZE);
        Page<SaldoInventario> page;
        do {
            page = listarExistencias(
                    productoId,
                    bodegaId,
                    paisId,
                    paisesPermitidos,
                    soloConExistencia,
                    pageable);
            for (SaldoInventario saldo : page) {
                escribirFila(
                        writer,
                        saldo.codigoProducto(),
                        saldo.nombreProducto(),
                        saldo.bodegaCodigo(),
                        saldo.bodegaNombre(),
                        saldo.cantidad(),
                        saldo.actualizadoEnUtc());
            }
            pageable = page.nextPageable();
        } while (page.hasNext());
        writer.flush();
    }

    @Transactional(readOnly = true)
    public void exportarMovimientos(
            UUID productoId,
            UUID bodegaId,
            UUID usuarioId,
            String tipo,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            UUID paisId,
            UUID monedaId,
            Set<String> paisesPermitidos,
            OutputStream output) throws IOException {
        validarPaisFiltro(paisId, paisesPermitidos);
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        escribirFila(
                writer,
                "producto_codigo",
                "bodega_nombre",
                "tipo",
                "cantidad",
                "referencia_tipo",
                "referencia_id",
                "usuario_id",
                "fecha_utc",
                "observacion");
        Pageable pageable = PageRequest.of(0, EXPORT_PAGE_SIZE);
        Page<MovimientoInventario> page;
        do {
            page = listarMovimientos(
                    productoId,
                    bodegaId,
                    usuarioId,
                    tipo,
                    desde,
                    hasta,
                    paisId,
                    monedaId,
                    paisesPermitidos,
                    pageable);
            for (MovimientoInventario movimiento : page) {
                escribirFila(
                        writer,
                        movimiento.codigoProducto(),
                        movimiento.bodegaNombre(),
                        movimiento.tipo(),
                        movimiento.cantidad(),
                        movimiento.referenciaTipo(),
                        movimiento.referenciaId(),
                        movimiento.usuarioId(),
                        movimiento.fechaUtc(),
                        movimiento.observacion());
            }
            pageable = page.nextPageable();
        } while (page.hasNext());
        writer.flush();
    }

    private void validarPaisFiltro(UUID paisId, Set<String> paisesPermitidos) {
        if (paisId == null || paisesPermitidos == null || paisesPermitidos.isEmpty()) {
            return;
        }
        Pais pais = configuracion.buscarPais(paisId)
                .orElseThrow(() -> new ErrorAplicacionException(
                        HttpStatus.NOT_FOUND, "error.sales.country.not-found"));
        accesoPais.validar(paisesPermitidos, pais.codigoIso());
    }

    private void escribirFila(Writer writer, Object... valores) throws IOException {
        for (int index = 0; index < valores.length; index++) {
            if (index > 0) {
                writer.write(',');
            }
            Object valor = valores[index];
            String texto = valor == null ? "" : valor.toString();
            if (valor instanceof CharSequence && pareceFormula(texto)) {
                texto = "'" + texto;
            }
            writer.write('"');
            writer.write(texto.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }

    private boolean pareceFormula(String value) {
        String trimmed = value.stripLeading();
        return !trimmed.isEmpty()
                && (trimmed.charAt(0) == '='
                        || trimmed.charAt(0) == '+'
                        || trimmed.charAt(0) == '-'
                        || trimmed.charAt(0) == '@'
                        || trimmed.charAt(0) == '\t'
                        || trimmed.charAt(0) == '\r');
    }
}
