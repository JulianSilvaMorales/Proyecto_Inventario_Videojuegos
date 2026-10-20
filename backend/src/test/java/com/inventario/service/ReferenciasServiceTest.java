package com.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.inventario.domain.Pais;
import com.inventario.domain.UsuarioAutenticado;
import com.inventario.repository.ConfiguracionRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ReferenciasServiceTest {

    @Mock
    private ConfiguracionService configuracion;

    @Mock
    private CatalogoService catalogo;

    @Test
    void limitaLasReferenciasDePaisAlAlcanceDelUsuario() {
        Pais colombia = new Pais(UUID.randomUUID(), "CO", "Colombia", null, null, null, true);
        Pais estadosUnidos = new Pais(UUID.randomUUID(), "US", "United States", null, null, null, true);
        when(configuracion.listarPaises(null, PageRequest.of(0, 500)))
                .thenReturn(new PageImpl<>(List.of(colombia, estadosUnidos)));
        ReferenciasService service = new ReferenciasService(configuracion, catalogo);

        var options = service.listar(
                "pais",
                new UsuarioAutenticado(
                        UUID.randomUUID(),
                        "seller@example.com",
                        "Seller",
                        "es",
                        List.of("VENDEDOR_CAJERO"),
                        Set.of("CLIENTES_GESTIONAR"),
                        Set.of("CO"),
                        UUID.randomUUID()));

        assertThat(options)
                .extracting(option -> option.codigo())
                .containsExactly("CO");
    }

    @Test
    void rechazaReferenciasDesconocidasOSinPermiso() {
        ReferenciasService service = new ReferenciasService(configuracion, catalogo);
        UsuarioAutenticado user = new UsuarioAutenticado(
                UUID.randomUUID(),
                "user@example.com",
                "User",
                "es",
                List.of(),
                Set.of(),
                Set.of(),
                UUID.randomUUID());

        assertThatThrownBy(() -> service.listar("tabla", user))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.listar(
                        "pais",
                        new UsuarioAutenticado(
                                user.usuarioId(),
                                user.correo(),
                                user.nombre(),
                                user.idioma(),
                                user.roles(),
                                Set.of("INVENTARIO_CONSULTAR"),
                                user.paises(),
                                user.sesionId())))
                .isInstanceOfSatisfying(
                        ErrorAplicacionException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
