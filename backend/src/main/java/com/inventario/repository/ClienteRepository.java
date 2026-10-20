package com.inventario.repository;

import com.inventario.domain.Cliente;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ClienteRepository {

    private static final String COLUMNS = """
            c.id, c.pais_id, c.clasificacion, c.nombre, c.apellidos, c.tipo_documento,
            c.documento, c.codigo_postal, c.direccion, c.telefono, c.correo, c.base_legal,
            c.consentimiento_registrado, c.consentimiento_en_utc, c.ultimo_contacto_en_utc,
            c.activo
            """;
    private static final RowMapper<Cliente> MAPPER = ClienteRepository::mapCliente;

    private final JdbcTemplate jdbc;

    public ClienteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<Cliente> listar(String buscar, Set<String> paisesPermitidos, Pageable pageable) {
        StringBuilder from = new StringBuilder(
                "FROM public.cliente c JOIN public.pais p ON p.id = c.pais_id WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (buscar != null && !buscar.isBlank()) {
            from.append("""
                     AND (lower(c.nombre) LIKE lower(?) OR lower(coalesce(c.apellidos, ''))
                     LIKE lower(?) OR lower(c.correo) LIKE lower(?))
                    """);
            String term = "%" + buscar.trim() + "%";
            args.add(term);
            args.add(term);
            args.add(term);
        }
        if (paisesPermitidos != null && !paisesPermitidos.isEmpty()) {
            List<String> paises = new TreeSet<>(paisesPermitidos).stream().toList();
            String placeholders = paises.stream().map(ignored -> "?").collect(Collectors.joining(","));
            from.append(" AND p.codigo_iso IN (").append(placeholders).append(")");
            args.addAll(paises);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) " + from,
                Long.class,
                args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());
        List<Cliente> clientes = jdbc.query(
                "SELECT " + COLUMNS + " " + from
                        + " ORDER BY lower(c.nombre), lower(coalesce(c.apellidos, ''))"
                        + " LIMIT ? OFFSET ?",
                MAPPER,
                pageArgs.toArray());
        return new PageImpl<>(clientes, pageable, total == null ? 0 : total);
    }

    public Optional<Cliente> buscar(UUID id) {
        try {
            Cliente cliente = jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM public.cliente c WHERE c.id = ?",
                    MAPPER,
                    id);
            return Optional.ofNullable(cliente);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public UUID crear(Cliente cliente) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO public.cliente
                    (id, pais_id, clasificacion, nombre, apellidos, tipo_documento, documento,
                     codigo_postal, direccion, telefono, correo, base_legal,
                     consentimiento_registrado, consentimiento_en_utc,
                     ultimo_contacto_en_utc, activo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
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
                cliente.activo());
        return id;
    }

    public boolean actualizar(Cliente cliente) {
        return jdbc.update(
                """
                UPDATE public.cliente
                SET clasificacion = ?, nombre = ?, apellidos = ?, tipo_documento = ?,
                    documento = ?, codigo_postal = ?, direccion = ?, telefono = ?,
                    correo = ?, base_legal = ?, consentimiento_registrado = ?,
                    consentimiento_en_utc = ?, activo = ?
                WHERE id = ? AND pais_id = ?
                """,
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
                cliente.activo(),
                cliente.id(),
                cliente.paisId()) == 1;
    }

    public void bloquear(UUID id) {
        jdbc.query(
                "SELECT id FROM public.cliente WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class),
                id);
    }

    public boolean desactivar(UUID id) {
        return jdbc.update(
                "UPDATE public.cliente SET activo = FALSE WHERE id = ? AND activo = TRUE",
                id) == 1;
    }

    private static Cliente mapCliente(ResultSet rs, int row) throws SQLException {
        return new Cliente(
                rs.getObject("id", UUID.class),
                rs.getObject("pais_id", UUID.class),
                rs.getString("clasificacion"),
                rs.getString("nombre"),
                rs.getString("apellidos"),
                rs.getString("tipo_documento"),
                rs.getString("documento"),
                rs.getString("codigo_postal"),
                rs.getString("direccion"),
                rs.getString("telefono"),
                rs.getString("correo"),
                rs.getString("base_legal"),
                rs.getBoolean("consentimiento_registrado"),
                rs.getObject("consentimiento_en_utc", OffsetDateTime.class),
                rs.getObject("ultimo_contacto_en_utc", OffsetDateTime.class),
                rs.getBoolean("activo"));
    }
}
