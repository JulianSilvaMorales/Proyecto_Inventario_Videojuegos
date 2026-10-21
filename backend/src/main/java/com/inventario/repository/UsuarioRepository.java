package com.inventario.repository;

import com.inventario.domain.IdentidadAutenticada;
import com.inventario.domain.UsuarioCuenta;
import com.inventario.dto.ReferenciaOpcion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UsuarioRepository {

    private static final RowMapper<UsuarioCuenta> ACCOUNT_ROW_MAPPER =
            UsuarioRepository::mapAccount;

    private final JdbcTemplate jdbcTemplate;

    public UsuarioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReferenciaOpcion> listarReferencias(int limite) {
        return jdbcTemplate.query(
                """
                SELECT id, correo, nombre
                FROM public.usuario
                ORDER BY nombre, correo
                LIMIT ?
                """,
                (rs, row) -> new ReferenciaOpcion(
                        rs.getObject("id", UUID.class),
                        rs.getString("correo"),
                        rs.getString("nombre")),
                limite);
    }

    public Optional<UsuarioCuenta> buscarPorCorreoParaAutenticar(String correo) {
        List<UsuarioCuenta> users = jdbcTemplate.query(
                """
                SELECT u.id, u.correo, u.nombre, u.hash_contrasena,
                       COALESCE(i.codigo_bcp47, 'es') AS idioma,
                       u.intentos_fallidos, u.bloqueado_hasta_utc, u.activo
                FROM public.usuario u
                LEFT JOIN public.idioma i ON i.id = u.idioma_id
                WHERE lower(u.correo) = lower(?)
                FOR UPDATE OF u
                """,
                ACCOUNT_ROW_MAPPER,
                correo);
        return users.stream().findFirst();
    }

    public void registrarIntentoFallido(UUID usuarioId, int intentos, Timestamp bloqueadoHasta) {
        jdbcTemplate.update(
                "UPDATE public.usuario SET intentos_fallidos = ?, bloqueado_hasta_utc = ? "
                        + "WHERE id = ?",
                intentos,
                bloqueadoHasta,
                usuarioId);
    }

    public void reiniciarIntentosFallidos(UUID usuarioId) {
        jdbcTemplate.update(
                "UPDATE public.usuario SET intentos_fallidos = 0, bloqueado_hasta_utc = NULL "
                        + "WHERE id = ?",
                usuarioId);
    }

    public boolean desbloquear(UUID usuarioId) {
        return jdbcTemplate.update(
                "UPDATE public.usuario SET intentos_fallidos = 0, bloqueado_hasta_utc = NULL "
                        + "WHERE id = ?",
                usuarioId) == 1;
    }

    public IdentidadAutenticada cargarIdentidad(UUID usuarioId) {
        UsuarioCuenta account = jdbcTemplate.queryForObject(
                """
                SELECT u.id, u.correo, u.nombre, u.hash_contrasena,
                       COALESCE(i.codigo_bcp47, 'es') AS idioma,
                       u.intentos_fallidos, u.bloqueado_hasta_utc, u.activo
                FROM public.usuario u
                LEFT JOIN public.idioma i ON i.id = u.idioma_id
                WHERE u.id = ?
                """,
                ACCOUNT_ROW_MAPPER,
                usuarioId);
        if (account == null) {
            throw new IllegalStateException("No se encontró el usuario autenticado.");
        }

        List<String> roles = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT r.codigo
                FROM public.usuario_rol ur
                JOIN public.rol r ON r.id = ur.rol_id
                WHERE ur.usuario_id = ? AND r.activo = TRUE
                ORDER BY r.codigo
                """,
                String.class,
                usuarioId);
        Set<String> permissions = new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                SELECT DISTINCT p.codigo
                FROM public.usuario_rol ur
                JOIN public.rol r ON r.id = ur.rol_id
                JOIN public.rol_permiso rp ON rp.rol_id = r.id
                JOIN public.permiso p ON p.id = rp.permiso_id
                WHERE ur.usuario_id = ? AND r.activo = TRUE
                ORDER BY p.codigo
                """,
                String.class,
                usuarioId));
        Set<String> countries = new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                SELECT DISTINCT p.codigo_iso
                FROM public.usuario_pais up
                JOIN public.pais p ON p.id = up.pais_id
                WHERE up.usuario_id = ?
                ORDER BY p.codigo_iso
                """,
                String.class,
                usuarioId));

        return new IdentidadAutenticada(
                account.id(),
                account.correo(),
                account.nombre(),
                account.idioma(),
                roles,
                permissions,
                countries);
    }

    private static UsuarioCuenta mapAccount(ResultSet resultSet, int rowNumber)
            throws SQLException {
        Timestamp blockedUntil = resultSet.getTimestamp("bloqueado_hasta_utc");
        return new UsuarioCuenta(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("correo"),
                resultSet.getString("nombre"),
                resultSet.getString("hash_contrasena"),
                resultSet.getString("idioma"),
                resultSet.getInt("intentos_fallidos"),
                blockedUntil == null ? null : blockedUntil.toInstant(),
                resultSet.getBoolean("activo"));
    }
}
