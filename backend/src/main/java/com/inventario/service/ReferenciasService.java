package com.inventario.service;

import com.inventario.domain.Categoria;
import com.inventario.domain.Idioma;
import com.inventario.domain.Moneda;
import com.inventario.domain.Pais;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.dto.ReferenciaOpcion;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenciasService {

    private static final int MAX_REFERENCES = 500;
    private static final Map<String, Set<String>> PERMISOS = Map.of(
            "pais",
            Set.of(
                    "PARAMETROS_GESTIONAR",
                    "COMPRAS_GESTIONAR",
                    "PROVEEDORES_GESTIONAR",
                    "CLIENTES_GESTIONAR",
                    "PRODUCTOS_GESTIONAR",
                    "VENTAS_GESTIONAR"),
            "moneda",
            Set.of(
                    "PARAMETROS_GESTIONAR",
                    "TIPOS_CAMBIO_GESTIONAR",
                    "COMPRAS_GESTIONAR",
                    "PROVEEDORES_GESTIONAR"),
            "idioma",
            Set.of("PARAMETROS_GESTIONAR"),
            "categoria",
            Set.of(
                    "CATALOGO_CONSULTAR",
                    "PRODUCTOS_GESTIONAR",
                    "PARAMETROS_GESTIONAR"));

    private final ConfiguracionService configuracion;
    private final CatalogoService catalogo;

    public ReferenciasService(ConfiguracionService configuracion, CatalogoService catalogo) {
        this.configuracion = configuracion;
        this.catalogo = catalogo;
    }

    @Transactional(readOnly = true)
    public List<ReferenciaOpcion> listar(String nombre, UsuarioAutenticado actor) {
        Set<String> permisos = PERMISOS.get(nombre);
        if (permisos == null) {
            throw new ErrorAplicacionException(HttpStatus.NOT_FOUND, "error.reference.not-found");
        }
        if (actor.permisos().stream().noneMatch(permisos::contains)) {
            throw new ErrorAplicacionException(HttpStatus.FORBIDDEN, "error.reference.forbidden");
        }

        PageRequest page = PageRequest.of(0, MAX_REFERENCES);
        return switch (nombre) {
            case "pais" -> configuracion.listarPaises(null, page).getContent().stream()
                    .filter(pais -> actor.paises() == null
                            || actor.paises().isEmpty()
                            || actor.paises().contains(pais.codigoIso()))
                    .map(ReferenciasService::opcionPais)
                    .toList();
            case "moneda" -> configuracion.listarMonedas(null, page).getContent().stream()
                    .map(ReferenciasService::opcionMoneda)
                    .toList();
            case "idioma" -> configuracion.listarIdiomas(null, page).getContent().stream()
                    .map(ReferenciasService::opcionIdioma)
                    .toList();
            case "categoria" -> catalogo.listarCategorias(null, page).getContent().stream()
                    .filter(Categoria::activo)
                    .map(ReferenciasService::opcionCategoria)
                    .toList();
            default -> throw new ErrorAplicacionException(
                    HttpStatus.NOT_FOUND, "error.reference.not-found");
        };
    }

    private static ReferenciaOpcion opcionPais(Pais pais) {
        return new ReferenciaOpcion(pais.id(), pais.codigoIso(), pais.nombre());
    }

    private static ReferenciaOpcion opcionMoneda(Moneda moneda) {
        return new ReferenciaOpcion(moneda.id(), moneda.codigoIso(), moneda.nombre());
    }

    private static ReferenciaOpcion opcionIdioma(Idioma idioma) {
        return new ReferenciaOpcion(idioma.id(), idioma.codigoBcp47(), idioma.nombre());
    }

    private static ReferenciaOpcion opcionCategoria(Categoria categoria) {
        return new ReferenciaOpcion(categoria.id(), categoria.codigo(), categoria.nombre());
    }
}
